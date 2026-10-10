package com.bibledesktop.shared.api
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable data class CommentaryAnnotations(@SerialName("source_sha256")val sourceSha256:String,val links:List<CommentaryAnnotationLink> = emptyList(),val media:List<CommentaryAnnotationMedia> = emptyList())
@Serializable data class CommentaryAnnotationLink(val kind:String,val href:String,val label:String,val status:String,@SerialName("book_slug")val bookSlug:String?=null,val chapter:Int?=null,val verse:Int?=null)
@Serializable data class CommentaryAnnotationMedia(val src:String?=null,val alt:String?=null,val status:String,@SerialName("fragment_id")val fragmentId:String?=null,val module:String?=null,val textual:String?=null,@SerialName("media_id")val mediaId:Long?=null,val url:String?=null,val sha256:String?=null,@SerialName("mime_type")val mimeType:String?=null,val bytes:Long?=null)
