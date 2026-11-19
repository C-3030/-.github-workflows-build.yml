package com.perol.asdpl.pixivez.ui.novel

import android.content.Context
import androidx.core.text.HtmlCompat
import com.perol.asdpl.pixivez.data.NovelDownload
import com.perol.asdpl.pixivez.data.NovelDownloadStore
import com.perol.asdpl.pixivez.data.model.Novel
import com.perol.asdpl.pixivez.services.PxEZApp
import com.perol.asdpl.pixivez.services.toLegal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 小说导出:TXT(纯文字)/ EPUB(图文,内嵌插图)。
 * 只依赖已解析好的 [NovelChunk] 列表,不重复请求 pixiv 接口;
 * 图片按块里的 URL 直接下载(失败就跳过,不中断导出)。
 */
object NovelExporter {
    enum class Format(val ext: String) {
        TXT("txt"),
        EPUB("epub")
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 导出目录:设置里的图片保存目录下的 Novel 子目录 */
    fun outputDir(): File = File(PxEZApp.storepath, "Novel").apply { mkdirs() }

    suspend fun export(
        context: Context,
        novel: Novel,
        chunks: List<NovelChunk>,
        format: Format
    ): File = withContext(Dispatchers.IO) {
        val name = buildString {
            append(novel.title.toLegal().ifBlank { "novel" })
            novel.series?.title?.let { append("_").append(it.toLegal()) }
            append("_").append(novel.id)
        }
        val dir = outputDir()
        val file = File(dir, "$name.${format.ext}")
        when (format) {
            Format.TXT -> writeTxt(file, novel, chunks)
            Format.EPUB -> writeEpub(file, novel, chunks)
        }
        // 记一笔,下载进度页据此把小说也列出来
        NovelDownloadStore.add(
            NovelDownload(
                id = novel.id,
                title = novel.title,
                author = novel.user.name,
                format = format.name,
                path = file.absolutePath,
                thumb = novel.image_urls.medium,
                size = file.length(),
                time = System.currentTimeMillis()
            )
        )
        file
    }

    /** 只下封面图(小说详情接口自带的封面,原图优先) */
    suspend fun downloadCover(novel: Novel): File = withContext(Dispatchers.IO) {
        val url = novel.image_urls.large.ifBlank { novel.image_urls.medium }
        val ext = extOf(url)
        val name = buildString {
            append(novel.title.toLegal().ifBlank { "novel" })
            append("_").append(novel.id).append("_cover").append(ext)
        }
        val file = File(outputDir(), name)
        val bytes = fetchBytes(url) ?: throw IllegalStateException("cover download failed")
        file.writeBytes(bytes)
        NovelDownloadStore.add(
            NovelDownload(
                id = novel.id,
                title = novel.title,
                author = novel.user.name,
                format = "COVER",
                path = file.absolutePath,
                thumb = url,
                size = file.length(),
                time = System.currentTimeMillis()
            )
        )
        file
    }

    // ---------------- TXT ----------------

    private fun writeTxt(file: File, novel: Novel, chunks: List<NovelChunk>) {
        OutputStreamWriter(file.outputStream(), Charsets.UTF_8).use { w ->
            w.write(novel.title)
            w.write("\r\n")
            w.write("作者:${novel.user.name}")
            w.write("\r\n")
            if (novel.tags.isNotEmpty()) {
                w.write("标签:" + novel.tags.joinToString(" ") { "#${it.name}" })
                w.write("\r\n")
            }
            w.write("地址:${novelUrl(novel.id)}")
            w.write("\r\n")
            if (novel.caption.isNotBlank()) {
                w.write("\r\n")
                w.write(plainCaption(novel.caption))
                w.write("\r\n")
            }
            w.write("\r\n----------\r\n\r\n")
            var lastPage = -1
            for (chunk in chunks) {
                if (chunk.page != lastPage) {
                    if (lastPage >= 0) w.write("\r\n\r\n")
                    lastPage = chunk.page
                }
                when (chunk) {
                    is NovelChunk.Text -> w.write(plainText(chunk.text))
                    is NovelChunk.Image -> {
                        w.write("\r\n[插图]")
                        chunk.url?.let { w.write(it) }
                        w.write("\r\n")
                    }
                }
            }
            w.write("\r\n\r\n———— 导出自 PixEz ————\r\n")
        }
    }

    // pixiv 正文标记 → 纯文字(章节标题、注音、链接可读化)
    fun plainText(text: String): String {
        val sb = StringBuilder()
        for (t in tokenize(text)) when (t) {
            is NovelToken.Plain -> sb.append(t.text)
            is NovelToken.Chapter -> sb.append("\n\n").append(t.title).append("\n\n")
            is NovelToken.Ruby -> sb.append(t.base).append("(").append(t.rt).append(")")
            is NovelToken.JumpUri -> sb.append(t.title.ifBlank { t.url })
            is NovelToken.JumpPage -> sb.append("(P").append(t.page).append(")")
        }
        return sb.toString()
    }

    private fun plainCaption(caption: String): String =
        HtmlCompat.fromHtml(caption, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

    // ---------------- EPUB ----------------

    private suspend fun writeEpub(file: File, novel: Novel, chunks: List<NovelChunk>) {
        // 先下载插图(最多 24 张,防止误下载超大体积)
        val images = LinkedHashMap<String, ByteArray>()
        for (chunk in chunks) {
            if (chunk !is NovelChunk.Image) continue
            val url = chunk.url ?: continue
            if (images.containsKey(url)) continue
            if (images.size >= MAX_IMAGES) break
            val bytes = fetchBytes(url) ?: continue
            images[url] = bytes
        }
        val imageEntryName = HashMap<String, String>()
        images.keys.forEachIndexed { i, url ->
            imageEntryName[url] = "images/img${i + 1}${extOf(url)}"
        }

        ZipOutputStream(file.outputStream()).use { zip ->
            // mimetype 必须是第一个且不压缩
            zip.setLevel(0)
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
            zip.setLevel(-1)

            put(zip, "META-INF/container.xml", CONTAINER_XML)
            put(zip, "OEBPS/style.css", STYLE_CSS)
            put(zip, "OEBPS/nav.xhtml", navXhtml(novel))
            put(zip, "OEBPS/toc.ncx", tocNcx(novel))
            put(zip, "OEBPS/content.opf", contentOpf(novel, imageEntryName.values.toList()))
            put(zip, "OEBPS/text/body.xhtml", bodyXhtml(novel, chunks, imageEntryName))
            for ((url, bytes) in images) {
                val entry = ZipEntry("OEBPS/" + imageEntryName[url])
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun put(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun fetchBytes(url: String): ByteArray? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) PixEz")
            .header("Referer", "https://www.pixiv.net/")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.bytes()
        }
    } catch (e: Exception) {
        null
    }

    private fun extOf(url: String): String = when {
        url.endsWith(".png", true) -> ".png"
        url.endsWith(".gif", true) -> ".gif"
        url.endsWith(".webp", true) -> ".webp"
        else -> ".jpg"
    }

    private fun mimeOf(entry: String): String = when {
        entry.endsWith(".png") -> "image/png"
        entry.endsWith(".gif") -> "image/gif"
        entry.endsWith(".webp") -> "image/webp"
        else -> "image/jpeg"
    }

    private fun bodyXhtml(
        novel: Novel,
        chunks: List<NovelChunk>,
        imageEntry: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.append(XML_HEAD)
        sb.append("<body><section class=\"title-page\">")
        sb.append("<h1>").append(esc(novel.title)).append("</h1>")
        sb.append("<p class=\"author\">").append(esc(novel.user.name)).append("</p>")
        if (novel.tags.isNotEmpty()) {
            sb.append("<p class=\"tags\">")
                .append(esc(novel.tags.joinToString(" ") { "#${it.name}" }))
                .append("</p>")
        }
        sb.append("<p class=\"source\">")
            .append(esc(novelUrl(novel.id)))
            .append("</p></section>")
        if (novel.caption.isNotBlank()) {
            sb.append("<section class=\"caption\">")
            plainCaption(novel.caption).split("\n").forEach {
                if (it.isNotBlank()) sb.append("<p>").append(esc(it)).append("</p>")
            }
            sb.append("</section>")
        }
        var openPage = -1
        for (chunk in chunks) {
            if (chunk.page != openPage) {
                if (openPage >= 0) sb.append("</section>")
                sb.append("<section class=\"page\">")
                openPage = chunk.page
            }
            when (chunk) {
                is NovelChunk.Text -> appendRichText(sb, chunk.text)
                is NovelChunk.Image -> {
                    val entry = chunk.url?.let { imageEntry[it] }
                    if (entry != null) {
                        sb.append("<div class=\"pic\"><img alt=\"illustration\" src=\"../")
                            .append(entry).append("\"/></div>")
                    }
                }
            }
        }
        if (openPage >= 0) sb.append("</section>")
        sb.append("<p class=\"tail\">导出自 PixEz</p>")
        sb.append("</body></html>")
        return sb.toString()
    }

    // 行内标记 → XHTML(章节标题/注音/链接)
    private fun appendRichText(sb: StringBuilder, text: String) {
        for (t in tokenize(text)) when (t) {
            is NovelToken.Plain -> sb.append(esc(t.text).replace("\n", "<br/>"))
            is NovelToken.Chapter -> sb.append("<h2>").append(esc(t.title)).append("</h2>")
            is NovelToken.Ruby -> sb.append(esc(t.base)).append("<rt>").append(esc(t.rt))
                .append("</rt>")
            is NovelToken.JumpUri -> sb.append("<a href=\"").append(esc(t.url)).append("\">")
                .append(esc(t.title.ifBlank { t.url })).append("</a>")
            is NovelToken.JumpPage -> sb.append("<span class=\"jump\">P")
                .append(t.page).append("</span>")
        }
    }

    private fun navXhtml(novel: Novel) = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>${esc(novel.title)}</title></head>
<body>
<nav epub:type="toc" id="toc"><h1>目录</h1>
<ol><li><a href="text/body.xhtml">${esc(novel.title)}</a></li></ol>
</nav>
</body>
</html>"""

    private fun tocNcx(novel: Novel) = """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="pixez-novel-${novel.id}"/></head>
<docTitle><text>${esc(novel.title)}</text></docTitle>
<navMap><navPoint id="np1" playOrder="1">
<navLabel><text>${esc(novel.title)}</text></navLabel>
<content src="text/body.xhtml"/>
</navPoint></navMap>
</ncx>"""

    private fun contentOpf(novel: Novel, images: List<String>) = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\" unique-identifier=\"bookid\">")
        append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
        append("<dc:title>").append(esc(novel.title)).append("</dc:title>")
        append("<dc:creator>").append(esc(novel.user.name)).append("</dc:creator>")
        append("<dc:language>zh</dc:language>")
        append("<dc:identifier id=\"bookid\">pixez-novel-").append(novel.id).append("</dc:identifier>")
        append("<dc:source>").append(esc(novelUrl(novel.id))).append("</dc:source>")
        append("</metadata><manifest>")
        append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>")
        append("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\"/>")
        append("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>")
        append("<item id=\"body\" href=\"text/body.xhtml\" media-type=\"application/xhtml+xml\"/>")
        images.forEachIndexed { i, path ->
            append("<item id=\"img").append(i + 1).append("\" href=\"").append(path)
                .append("\" media-type=\"").append(mimeOf(path)).append("\"/>")
        }
        append("</manifest><spine toc=\"ncx\"><itemref idref=\"body\"/></spine></package>")
    }

    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun novelUrl(id: Int) = "https://www.pixiv.net/novel/show.php?id=$id"

    private const val MAX_IMAGES = 24
    private const val XML_HEAD =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head><title>body</title><link rel=\"stylesheet\" type=\"text/css\" href=\"../style.css\"/></head>"
    private const val CONTAINER_XML = """<?xml version="1.0" encoding="utf-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
    private const val STYLE_CSS = """body { font-family: serif; line-height: 1.7; margin: 1em; }
h1 { font-size: 1.4em; }
h2 { font-size: 1.15em; text-align: center; margin: 1.2em 0 0.8em; }
p { text-indent: 2em; margin: 0.4em 0; }
.title-page p, .caption p, p.tail { text-indent: 0; }
.author { color: #666; }
.tags { color: #888; font-size: 0.9em; }
.source { color: #999; font-size: 0.8em; }
.caption { margin: 1em 0; padding: 0.6em; border-top: 1px solid #ddd; border-bottom: 1px solid #ddd; }
.pic { text-align: center; margin: 1em 0; }
.pic img { max-width: 100%; }
.jump { color: #888; }
.tail { color: #aaa; font-size: 0.8em; text-align: center; margin-top: 2em; }"""
}
