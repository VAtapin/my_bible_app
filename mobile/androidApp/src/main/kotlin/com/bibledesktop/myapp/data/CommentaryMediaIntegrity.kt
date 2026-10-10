package com.bibledesktop.myapp.data

import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/** Actual file identity is checked before an image can be installed or displayed. */
internal fun verifyCommentaryMediaFile(file:File,sha256:String,bytes:Long,mime:String,width:Int?=null,height:Int?=null){
 require(Regex("[a-f0-9]{64}").matches(sha256)&&bytes in 1..10*1024*1024L&&file.isFile&&file.length()==bytes)
 val prefix=ByteArray(12);file.inputStream().use{it.read(prefix)}
 val signature=when(mime){
  "image/png"->prefix.take(8).map{it.toInt() and 255}==listOf(137,80,78,71,13,10,26,10)
  "image/jpeg"->prefix.take(3).map{it.toInt() and 255}==listOf(255,216,255)
  "image/gif"->prefix.copyOfRange(0,6).decodeToString() in listOf("GIF87a","GIF89a")
  "image/webp"->prefix.copyOfRange(0,4).decodeToString()=="RIFF"&&prefix.copyOfRange(8,12).decodeToString()=="WEBP"
  else->false
 };require(signature)
 val hash=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val buffer=ByteArray(65536);while(true){val count=input.read(buffer);if(count<0)break;hash.update(buffer,0,count)}}
 require(hash.digest().joinToString(""){"%02x".format(it)}==sha256)
 val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
 require(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outMimeType==mime)
 require((width==null||width==bounds.outWidth)&&(height==null||height==bounds.outHeight))
}
