package com.perol.asdpl.pixivez.ui.manager

import com.ketch.DownloadModel
import java.io.File

/**
 * 下载页统一的一行:可能来自 ketch 任务、导出记录,或磁盘上扫描到的文件。
 * 这样插画/小说两栏都能用同一套卡片展示。
 */
data class DownloadEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val progress: Int,
    val isNovel: Boolean,
    val file: File? = null,
    val coverUrl: String? = null,
    val illustId: Int? = null,
    val novelId: Int? = null,
    val model: DownloadModel? = null,
    val isTemp: Boolean = false,
) {
    val isDone: Boolean
        get() = when {
            isTemp -> false
            model != null -> model.status == com.ketch.Status.SUCCESS
            file != null -> true
            else -> true
        }
}
