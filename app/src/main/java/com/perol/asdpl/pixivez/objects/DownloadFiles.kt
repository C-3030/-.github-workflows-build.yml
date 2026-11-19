package com.perol.asdpl.pixivez.objects

import java.io.File

/** 下载目录的扫描/整理:列出文件、把零散的 .temp 缓存收进 .cache */
object DownloadFiles {
    private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    private val novelExt = setOf("txt", "epub", "html")

    fun isTemp(file: File) = file.name.endsWith(".temp", true) ||
        file.name.contains(".temp", true)

    fun isImage(file: File) = !isTemp(file) && file.extension.lowercase() in imageExt

    fun isNovel(file: File) = !isTemp(file) && file.extension.lowercase() in novelExt

    /** 列出目录下的文件(可选递归,默认不递归;跳过隐藏目录) */
    fun list(dir: File, recursive: Boolean = false): List<File> {
        if (!dir.isDirectory) return emptyList()
        val out = mutableListOf<File>()
        dir.listFiles()?.forEach { f ->
            when {
                f.isDirectory -> {
                    if (recursive && !f.name.startsWith(".")) out.addAll(list(f, true))
                }

                f.isFile && !f.name.startsWith(".") -> out.add(f)
            }
        }
        return out
    }

    /** 把散落的、没有下载任务认领的 .temp 收进 <store>/.cache,返回移动了多少个。
     *  keepNames 里是仍在队列里的下载文件名(断开 .temp 后),这些要留着才能续传。 */
    fun stashTemps(store: File, keepNames: Set<String> = emptySet()): Int {
        val cache = File(store, ".cache").apply { mkdirs() }
        var moved = 0
        listOf(store, File(store, "Novel")).forEach { dir ->
            list(dir).filter { isTemp(it) && it.name.removeSuffix(".temp") !in keepNames }
                .forEach { file ->
                try {
                    val target = File(cache, file.name)
                    if (target.exists()) target.delete()
                    if (file.renameTo(target)) moved++
                } catch (e: Exception) {
                    // 移动失败就留在原地
                }
            }
        }
        return moved
    }
}
