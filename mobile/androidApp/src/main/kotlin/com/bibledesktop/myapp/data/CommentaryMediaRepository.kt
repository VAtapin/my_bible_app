package com.bibledesktop.myapp.data
import android.content.Context
import com.bibledesktop.shared.api.CommentaryAnnotationMedia
import java.io.File
import java.net.URI
import java.net.HttpURLConnection
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

internal class CommentaryMediaRepository(context:Context,private val packages:StudyPackageStore=StudyPackageStore(context),private val base:String="https://bible-desktop.com/api"){
 private val directory=File(context.noBackupFilesDir,"commentary-images-v1")
 companion object{private val mutex=Mutex()}
 private fun verify(file:File,media:CommentaryAnnotationMedia)=verifyCommentaryMediaFile(file,requireNotNull(media.sha256),requireNotNull(media.bytes),requireNotNull(media.mimeType))
 suspend fun load(code:String,media:CommentaryAnnotationMedia):File=withContext(Dispatchers.IO){mutex.withLock{
  require(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*").matches(code)&&media.status=="resolved"&&(media.mediaId?:0)>0&&Regex("[a-f0-9]{64}").matches(media.sha256.orEmpty())&&(media.bytes?:0)in 1..10*1024*1024L&&media.mimeType in listOf("image/png","image/jpeg","image/webp","image/gif"))
  val origin=URI(base);val url=origin.resolve(media.url?:error("Missing commentary image URL"));require(url.scheme==origin.scheme&&url.host==origin.host&&url.port==origin.port&&url.rawPath=="/api/commentary-modules/$code/media/${media.mediaId}"&&url.rawQuery==null&&url.rawFragment==null&&url.userInfo==null)
  if(packages.has(code)){require(packages.metadata(code)?.get("kind")?.jsonPrimitive?.content=="commentary");val row=packages.rows(code,"media",ids=listOf(media.sha256!!),limit=1).rows.firstOrNull()?:error("Installed image missing");require(row["api_id"]?.jsonPrimitive?.longOrNull==media.mediaId&&row["bytes"]?.jsonPrimitive?.longOrNull==media.bytes&&row["mime_type"]?.jsonPrimitive?.content==media.mimeType);val file=packages.media(code,media.sha256!!)?:error("Installed image file missing");verify(file,media);return@withLock file}
  val target=File(directory,"$code-${media.sha256}");if(target.isFile){verify(target,media);return@withLock target};check(directory.mkdirs()||directory.isDirectory)
  val temporary=File(directory,"${target.name}.${UUID.randomUUID()}.part");val connection=url.toURL().openConnection()as HttpURLConnection
  connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=15000
  try{require(connection.responseCode==200&&connection.contentType?.substringBefore(';')==media.mimeType);connection.inputStream.use{input->temporary.outputStream().use{output->val buffer=ByteArray(65536);var size=0L;while(true){val count=input.read(buffer);if(count<0)break;size+=count;require(size<=media.bytes!!);output.write(buffer,0,count)}}};verify(temporary,media);check(temporary.renameTo(target));target}finally{connection.disconnect();temporary.delete()}
 }}
}
