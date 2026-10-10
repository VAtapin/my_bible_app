package com.bibledesktop.myapp.data

import com.bibledesktop.shared.api.ReferenceVersification
import kotlinx.serialization.json.*
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneOffset

private fun JsonObject.publishedString(key:String)=(get(key) as? JsonPrimitive)?.takeIf{it.isString}?.content?.takeIf{it.isNotBlank()}
private fun publishedUri(value:String?)=value!=null&&runCatching{URI(value).let{it.scheme in listOf("https","http")&&!it.host.isNullOrBlank()&&it.userInfo==null}}.getOrDefault(false)
private fun publishedSha(value:String?)=value!=null&&Regex("[a-fA-F0-9]{64}").matches(value)
private fun verifiedDate(value:String?):Instant?=value?.let{runCatching{Instant.parse(it)}.getOrNull()?:runCatching{OffsetDateTime.parse(it).toInstant()}.getOrNull()?:runCatching{LocalDateTime.parse(it.replace(' ','T')).toInstant(ZoneOffset.UTC)}.getOrNull()}

/** The package can establish identity only; map entries never silently remap a stored pair. */
internal class StudyVersification private constructor(private val packages:StudyPackageStore){
 private val profiles=mutableMapOf<Pair<String,String>,String?>()
 private val maps=mutableMapOf<Pair<String,String>,List<JsonObject>>()
 private val entries=mutableMapOf<Pair<Long,String>,StudyPackageRows>()
 companion object {
  const val Code="CROSS_REFERENCES"
  val Tables=listOf("versification_profiles","versification_assignments","versification_map_sets","versification_map_entries")
  suspend fun create(packages:StudyPackageStore):StudyVersification? {
   val metadata=packages.metadata(Code)?:return null
   if(metadata.publishedString("kind")!="cross_references")return null
   val flag=metadata["versification_schema"] as? JsonPrimitive
   if(flag==null||flag.isString||flag.intOrNull!=1||Tables.any{!packages.hasTable(Code,it)})return null
   return StudyVersification(packages)
  }
 }
 private suspend fun profile(type:String,code:String):String? {
  val key=type to code;if(key in profiles)return profiles[key]
  val assignments=packages.rows(Code,"versification_assignments",subjectType=type,subjectCode=code,limit=2)
  val assignment=assignments.rows.singleOrNull()?.takeIf{assignments.total==1&&it.publishedString("subject_type")==type&&it.publishedString("subject_code")==code&&publishedUri(it.publishedString("evidence_uri"))}
  val profileCode=assignment?.publishedString("profile_code")
  val rows=profileCode?.let{packages.rows(Code,"versification_profiles",ids=listOf(it),limit=2)}
  val profile=rows?.rows?.singleOrNull()?.takeIf{rows?.total==1&&it.publishedString("code")==profileCode&&publishedUri(it.publishedString("provenance_uri"))&&publishedSha(it.publishedString("sha256"))&&it.publishedString("revision")?.takeIf{r->r!="0"}!=null}
  val result=profileCode.takeIf{profile!=null};profiles[key]=result;return result
 }
 private suspend fun maps(source:String,target:String):List<JsonObject>{
  val key=source to target;maps[key]?.let{return it}
  // Profiles and map revisions are small metadata; million-row map entries remain indexed on disk.
  val result=mutableListOf<JsonObject>();var offset=0
  do{val page=packages.rows(Code,"versification_map_sets",sourceProfile=source,targetProfile=target,offset=offset,limit=500,newestMaps=true);result+=page.rows;offset+=page.rows.size}while(offset<page.total)
  val ordered=result.sortedWith(compareByDescending<JsonObject>{verifiedDate(it.publishedString("verified_at"))?:Instant.MIN}.thenByDescending{(it["id"] as? JsonPrimitive)?.longOrNull?:0})
  maps[key]=ordered;return ordered
 }
 private suspend fun candidates(set:Long,ref:String):StudyPackageRows {
  val key=set to ref;entries[key]?.let{return it}
  val page=packages.rows(Code,"versification_map_entries",mapSetId=set,sourceOsis=ref,limit=2);entries[key]=page;return page
 }
 suspend fun reference(source:String,sourceRef:String,targetRef:String,edition:String):ReferenceVersification {
  if(!listOf(sourceRef,targetRef).all{Regex("[A-Za-z0-9]+\\.[1-9]\\d*\\.[1-9]\\d*").matches(it)})return ReferenceVersification()
  val from=profile("reference_source",source);val to=profile("translation",edition)
  var status=ReferenceVersification(sourceProfile=from,editionProfile=to)
  if(from==null||to==null)return status
  status=status.copy(status="raw")
  for(map in maps(from,to)){
   val id=(map["id"] as? JsonPrimitive)?.takeUnless{it.isString}?.longOrNull?:continue
   val version=map.publishedString("version")?:continue
   if(id<=0||map.publishedString("source_profile_code")!=from||map.publishedString("target_profile_code")!=to||verifiedDate(map.publishedString("verified_at"))==null||map.publishedString("reviewer")==null||!publishedUri(map.publishedString("provenance_uri"))||!publishedSha(map.publishedString("sha256")))continue
   status=status.copy(mapVersion=version)
   for(ref in listOf(sourceRef,targetRef).distinct()){
    val page=candidates(id,ref)
    if(page.total>1||page.rows.any{it.publishedString("relation")=="ambiguous"})return status.copy(status="ambiguous")
    val entry=page.rows.singleOrNull()
    if(entry==null||(entry["map_set_id"] as? JsonPrimitive)?.takeUnless{it.isString}?.longOrNull!=id||entry.publishedString("relation")!="exact"||entry.publishedString("source_osis_ref")!=ref||entry.publishedString("target_osis_ref")!=ref||!publishedUri(entry.publishedString("evidence_uri"))||entry.publishedString("evidence_id")==null)return status
   }
   return status.copy(status="verified")
  }
  return status
 }
}
