package com.perol.asdpl.pixivez.objects

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.perol.asdpl.pixivez.BuildConfig
import com.perol.asdpl.pixivez.R
import java.io.File

/** 用系统里的其他应用打开下载好的文件 */
object FileOpenUtil {
    fun open(context: Context, file: File) {
        if (!file.exists()) {
            Toasty.error(context, R.string.file_not_found)
            return
        }
        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Toasty.error(context, R.string.file_open_failed)
            return
        }
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent.createChooser(intent, file.name))
        } catch (e: Exception) {
            Toasty.error(context, R.string.file_open_failed)
        }
    }
}
