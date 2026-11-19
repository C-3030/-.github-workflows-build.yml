/*
 * MIT License
 *
 * Copyright (c) 2020 ultranity
 * Copyright (c) 2019 Perol_Notsfsssf
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE
 */

package com.perol.asdpl.pixivez.ui.manager

import android.annotation.SuppressLint
import com.chad.brvah.BaseQuickAdapter
import com.chad.brvah.viewholder.BaseViewHolder
import com.ketch.DownloadModel
import com.ketch.Status
import com.perol.asdpl.pixivez.R
import com.perol.asdpl.pixivez.base.MaterialDialogs
import com.perol.asdpl.pixivez.databinding.ItemDownloadTaskBinding
import com.perol.asdpl.pixivez.networks.ServiceFactory.gson
import com.perol.asdpl.pixivez.objects.ViewBindingUtil.getBinding
import com.perol.asdpl.pixivez.services.IllustD
import com.perol.asdpl.pixivez.services.PxEZApp
import com.perol.asdpl.pixivez.objects.FileOpenUtil
import com.perol.asdpl.pixivez.ui.pic.PictureActivity
import com.bumptech.glide.Glide
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

@SuppressLint("CheckResult")
class DownloadTaskAdapter :
    BaseQuickAdapter<DownloadModel, BaseViewHolder>(R.layout.item_download_row) {
    init {
        this.setOnItemClickListener { adapter, view, position ->
            val item = data[position]
            // 下载好的文件用第三方应用打开
            if (item.status == Status.SUCCESS) {
                FileOpenUtil.open(context, File(item.path, item.fileName))
            } else {
                val illust = gson.decodeFromString<IllustD>(item.metaData)
                PictureActivity.start(context, id = illust.id)
            }
        }
        this.setOnItemLongClickListener { adapter, view, position ->
            val item = data[position]
            //val illustD = gson.decodeFromString<IllustD>(item.metaData)
            MaterialDialogs(context).show {
                //setTitle("${illustD.id}_${illustD.part}")
                setTitle(item.fileName)
                setItems(R.array.download_task_choice) { _, index ->
                    when (index) {
                        0 -> MaterialDialogs(context).show {
                            setTitle(item.fileName)
                            setMessage(if (item.status == Status.FAILED) item.failureReason else item.metaData)
                            setPositiveButton(R.string.ok) { _, _ -> }
                        }
                        1 -> PxEZApp.instance.ketch.retry(item.id)
                        2 -> PxEZApp.instance.ketch.pause(item.id)
                        3 -> PxEZApp.instance.ketch.resume(item.id)
                        4 -> {
                            //PxEZApp.instance.ketch.cancel(item.id)
                            // use clear to del temp file if not finished
                            PxEZApp.instance.ketch.clearDb(item.id, item.status != Status.SUCCESS)
                        }
                    }
                }
            }
            true
        }
    }

    companion object {
        private const val MAX_PERCENT = 100
        private const val VALUE_60 = 60
        private const val VALUE_3 = 3
        private const val VALUE_300 = 300
        private const val VALUE_500 = 500
        private const val VALUE_1024 = 1024
        private const val SEC_IN_MILLIS = 1000
        private fun getTimeLeftText(
            speedInBPerMs: Float,
            progressPercent: Int,
            lengthInBytes: Long
        ): String {
            if (speedInBPerMs == 0F) return ""
            val speedInBPerSecond = speedInBPerMs * SEC_IN_MILLIS
            val bytesLeft =
                (lengthInBytes * (MAX_PERCENT - progressPercent) / MAX_PERCENT).toFloat()

            val secondsLeft = bytesLeft / speedInBPerSecond
            val minutesLeft = secondsLeft / VALUE_60
            val hoursLeft = minutesLeft / VALUE_60

            return when {
                secondsLeft < VALUE_60 -> "%.0f s left".format(secondsLeft)
                minutesLeft < VALUE_3 -> "%.0f mins %.0f s left".format(
                    minutesLeft,
                    secondsLeft % VALUE_60
                )

                minutesLeft < VALUE_60 -> "%.0f mins left".format(minutesLeft)
                minutesLeft < VALUE_300 -> "%.0f hrs %.0f mins left".format(
                    hoursLeft,
                    minutesLeft % VALUE_60
                )

                else -> "%.0f hrs left".format(hoursLeft)
            }
        }

        private fun getSpeedText(speedInBPerMs: Float): String {
            var value = speedInBPerMs * SEC_IN_MILLIS
            val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
            var unitIndex = 0

            while (value >= VALUE_500 && unitIndex < units.size - 1) {
                value /= VALUE_1024
                unitIndex++
            }

            return "%.2f %s".format(value, units[unitIndex])
        }

        private fun getTotalLengthText(lengthInBytes: Long): String {
            var value = lengthInBytes.toFloat()
            val units = arrayOf("B", "KB", "MB", "GB")
            var unitIndex = 0

            while (value >= VALUE_500 && unitIndex < units.size - 1) {
                value /= VALUE_1024
                unitIndex++
            }

            return "%.2f %s".format(value, units[unitIndex])
        }

        private fun getCompleteText(
            status: Status,
            speedInBPerMs: Float,
            progress: Int,
            length: Long
        ): String {
            if (status != Status.PROGRESS) {
                return status.name
            }
            val timeLeftText = getTimeLeftText(speedInBPerMs, progress, length)
            val speedText = getSpeedText(speedInBPerMs)

            val parts = mutableListOf<String>()

            if (timeLeftText.isNotEmpty()) {
                parts.add(timeLeftText)
            }

            if (speedText.isNotEmpty()) {
                parts.add(speedText)
            }

            return parts.joinToString(", ")
        }
    }

    @SuppressLint("SetTextI18n")
    override fun convert(holder: BaseViewHolder, item: DownloadModel, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            holder.getView<android.widget.ProgressBar>(R.id.row_progress).progress = item.progress
            holder.setText(R.id.row_status, statusText(item))
        }
    }

    private fun statusText(item: DownloadModel): String =
        getCompleteText(item.status, item.speedInBytePerMs, item.progress, item.total)

    @SuppressLint("SetTextI18n")
    override fun convert(holder: BaseViewHolder, item: DownloadModel) {
        holder.getView<android.widget.ProgressBar>(R.id.row_progress).progress = item.progress
        holder.setText(R.id.row_status, statusText(item))
        var title = item.fileName
        try {
            val illustD = gson.decodeFromString<IllustD>(item.metaData)
            title = "${illustD.title}${illustD.pString("_p")}"
        } catch (e: Exception) {
            // metaData 不合法时退回文件名
        }
        holder.setText(R.id.row_title, title)
        // 封面:已下载就用本地文件,否则用图片地址(Glide 已带 pixiv 头)
        val cover = holder.getView<android.widget.ImageView>(R.id.row_cover)
        val local = File(item.path, item.fileName)
        Glide.with(cover)
            .load(if (local.exists()) local else item.url)
            .centerCrop()
            .into(cover)
    }
}
