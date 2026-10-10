package com.bibledesktop.myapp.data

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

private fun JSONObject.actualLong(name:String):Long {val value=get(name);require(value is Int||value is Long);return (value as Number).toLong()}
private fun JSONObject.dimension(name:String):Int?=if(isNull(name))null else actualLong(name).let{value->value.toInt().also{require(it.toLong()==value)}}

/** Indexed rows bind media to this module's actual entry annotations, without loading the whole corpus. */
internal suspend fun validateStudyCommentaryMedia(directory:File,code:String,baseUrl:String){
 val origin=URI(baseUrl)
 SQLiteDatabase.openDatabase(File(directory,"index.sqlite").path,null,SQLiteDatabase.OPEN_READWRITE).use{db->
  db.beginTransaction()
  try{
  db.execSQL("CREATE TEMP TABLE verified_media(sha TEXT PRIMARY KEY,api_id INTEGER UNIQUE,bytes INTEGER,mime TEXT,referenced INTEGER NOT NULL DEFAULT 0)")
  var count=0
  db.rawQuery("SELECT payload FROM records WHERE table_name='media'",null).use{cursor->while(cursor.moveToNext()){
   currentCoroutineContext().ensureActive();val row=JSONObject(cursor.getString(0));val sha=row.getString("id");val id=row.actualLong("api_id");val mime=row.getString("mime_type");val bytes=row.actualLong("bytes")
   val extension=when(mime){"image/png"->"png";"image/jpeg"->"jpg";"image/webp"->"webp";"image/gif"->"gif";else->error("Unsupported commentary image type")}
   require(id>0&&Regex("[a-f0-9]{64}").matches(sha)&&row.getString("path")=="media/$sha.$extension")
   val file=File(directory,row.getString("path"));require(file.canonicalPath.startsWith(directory.canonicalPath+File.separator))
   verifyCommentaryMediaFile(file,sha,bytes,mime,row.dimension("width"),row.dimension("height"))
   db.execSQL("INSERT INTO verified_media(sha,api_id,bytes,mime) VALUES(?,?,?,?)",arrayOf<Any>(sha,id,bytes,mime));count++
  }}
  File(directory,"entries.jsonl").bufferedReader().use{reader->var line=reader.readLine();while(line!=null){
   currentCoroutineContext().ensureActive()
   if(line.isNotBlank()){
    val row=JSONObject(line);val annotations=row.optJSONObject("annotations");val media=annotations?.optJSONArray("media")
    if(media!=null&&annotations!=null)for(index in 0 until media.length()){
     val item=media.getJSONObject(index);if(item.optString("status")!="resolved")continue
     require(Regex("[a-f0-9]{64}").matches(annotations.getString("source_sha256")))
     val sha=item.getString("sha256");val id=item.actualLong("media_id");val bytes=item.actualLong("bytes");val mime=item.getString("mime_type");val url=origin.resolve(item.getString("url"))
     require(id>0&&url.scheme==origin.scheme&&url.host==origin.host&&url.port==origin.port&&url.userInfo==null&&url.rawQuery==null&&url.rawFragment==null&&url.path==origin.path.trimEnd('/')+"/commentary-modules/$code/media/$id")
     val owned=db.rawQuery("SELECT count(*) FROM verified_media WHERE sha=? AND api_id=? AND bytes=? AND mime=?",arrayOf(sha,id.toString(),bytes.toString(),mime)).use{it.moveToFirst();it.getInt(0)==1};require(owned)
     db.execSQL("UPDATE verified_media SET referenced=1 WHERE sha=?",arrayOf(sha))
    }
   }
   line=reader.readLine()
  }}
  require(db.rawQuery("SELECT count(*) FROM verified_media WHERE referenced=0",null).use{it.moveToFirst();it.getInt(0)}==0)
  require((File(directory,"media").listFiles()?.size?:0)==count)
  db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
}
