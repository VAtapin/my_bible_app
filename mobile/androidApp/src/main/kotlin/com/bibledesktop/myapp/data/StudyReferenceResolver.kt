package com.bibledesktop.myapp.data

import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

private fun JsonObject.number(key:String)=(get(key) as? JsonPrimitive)?.takeUnless{it.isString}?.longOrNull
private fun wholeReference(value:String?)=value!=null&&Regex("[A-Za-z0-9]+\\.[1-9]\\d*\\.[1-9]\\d*").matches(value)
private fun JsonObject.digestMatches(key:String,actual:String,required:Boolean):Boolean {
    val value=get(key)
    if(value==null||value==JsonNull)return !required
    return value is JsonPrimitive&&value.isString&&publishedSha(value.content)&&value.content.equals(actual,true)
}
private fun JsonObject.exactEntry()=publishedString("relation")=="exact"&&(get("part")==null||get("part")==JsonNull)&&
    (get("cardinality")==null||get("cardinality")==JsonNull||publishedString("cardinality")=="one-to-one")
private fun JsonObject.hasContentFingerprint()=get("target_content_sha256")?.let{it!=JsonNull}==true

/** Indexed inverse lookup first, then each target's forward map. Missing coverage never means identity. */
internal class StudyReferenceResolver(private val packages:StudyPackageStore,private val verifier:StudyVersification,private val client:BibleContentSource) {
    private val actual=mutableMapOf<String,ReferenceTarget?>()
    private val chapters=mutableMapOf<Pair<String,Int>,BibleChapter>()
    private suspend fun rows(table:String,osis:String?=null,map:Long?=null):List<JsonObject> {
        val output=mutableListOf<JsonObject>();var offset=0
        do {
            val page=packages.rows(StudyVersification.Code,table,sourceOsis=osis,mapSetId=map,offset=offset,limit=500)
            output+=page.rows;offset+=page.rows.size
        } while(offset<page.total)
        return output
    }
    private suspend fun passage(code:String,osis:String):ReferenceTarget? {
        if(osis in actual)return actual[osis]
        val value=try {
            val parts=osis.split('.')
            val location=client.getVerseLocations(code,listOf(osis)).singleOrNull()?.takeIf{it.osis==osis&&it.chapter==parts[1].toInt()&&it.verse==parts[2].toInt()}
            location?.let { place ->
                val key=place.book to place.chapter
                val chapter=chapters[key]?:client.getChapter(code,place.book,place.chapter).also { chapters[key]=it }
                require(chapter.translation.code==code&&chapter.book.slug==place.book&&chapter.chapter.number==place.chapter)
                chapter.verses.singleOrNull { it.id==place.verseId&&it.osisRef==osis&&it.number==place.verse&&it.plainText.isNotBlank() }
                    ?.let { ReferenceTarget(it.id,osis,"${chapter.book.name} ${place.chapter}:${place.verse}",place.book,place.chapter,place.verse,it.plainText) }
            }
        } catch(cancelled:CancellationException) { throw cancelled } catch(_:Exception) { null }
        actual[osis]=value
        return value
    }
    private fun validEntry(entry:JsonObject,map:Long,source:String):Boolean = entry.number("map_set_id")==map&&entry.publishedString("source_osis_ref")==source&&
        wholeReference(entry.publishedString("target_osis_ref"))&&entry.publishedString("relation") in listOf("exact","ambiguous")&&
        publishedUri(entry.publishedString("evidence_uri"))&&entry.publishedString("evidence_id")!=null&&(entry["part"]==null||entry["part"]==JsonNull)
    private suspend fun forward(map:Long,source:String):List<JsonObject> = rows("versification_map_entries",source,map).let { entries -> if(entries.all{validEntry(it,map,source)})entries else emptyList() }
    private suspend fun inverse(map:Long,target:String):List<JsonObject> = rows("versification_inverse_entries",target,map).let { entries -> if(entries.all {
        it.number("map_set_id")==map&&it.publishedString("target_osis_ref")==target&&wholeReference(it.publishedString("source_osis_ref"))&&
            it.publishedString("relation") in listOf("exact","ambiguous")&&publishedUri(it.publishedString("evidence_uri"))&&it.publishedString("evidence_id")!=null&&(it["part"]==null||it["part"]==JsonNull)
    })entries else emptyList() }
    private fun reciprocal(forward:JsonObject,inverse:JsonObject)=inverse.exactEntry()&&
        inverse.publishedString("source_osis_ref")==forward.publishedString("source_osis_ref")&&inverse.publishedString("target_osis_ref")==forward.publishedString("target_osis_ref")&&
        inverse.publishedString("evidence_id")==forward.publishedString("evidence_id")&&inverse.publishedString("evidence_uri")==forward.publishedString("evidence_uri")
    private fun resolved(entry:JsonObject,cardinality:String,target:ReferenceTarget?=null)=ResolvedReference(
        entry.getValue("target_osis_ref").jsonPrimitive.content,if(cardinality=="one-to-one")entry.getValue("relation").jsonPrimitive.content else "ambiguous",cardinality,
        entry.getValue("evidence_uri").jsonPrimitive.content,entry.getValue("evidence_id").jsonPrimitive.content,
        verseId=target?.verseId,bookSlug=target?.bookSlug,chapterNumber=target?.chapterNumber,verseNumber=target?.verseNumber,reference=target?.reference,text=target?.text)
    private fun legacy(row:JsonObject,code:String):CrossReference {
        val source=row.getValue("source_verse").jsonObject;val target=row.getValue("target_verse").jsonObject
        val metadata=row["metadata"] as? JsonObject
        val osis=target.getValue("osis_ref").jsonPrimitive.content
        return CrossReference(row.getValue("api_id").jsonPrimitive.long,
            ReferenceTarget(target.getValue("api_id").jsonPrimitive.long,osis,osis,target.getValue("book_slug").jsonPrimitive.content,target.getValue("chapter").jsonPrimitive.int,target.getValue("verse").jsonPrimitive.int),
            row["type"]?.jsonPrimitive?.contentOrNull,code,
            CrossReferenceMetadata(metadata?.get("legacy_quote_id")?.jsonPrimitive?.longOrNull,metadata?.get("raw_ref")?.jsonPrimitive?.contentOrNull),
            originalSource=OriginalReference(source.getValue("api_id").jsonPrimitive.long,source.getValue("osis_ref").jsonPrimitive.content),
            originalTarget=OriginalReference(target.getValue("api_id").jsonPrimitive.long,osis))
    }
    suspend fun references(verseId:Long,code:String,query:String):CrossReferences {
        require(verifier.schema==2&&wholeReference(query))
        val references=mutableListOf<CrossReference>();val admissions=mutableListOf<ReferenceQuerySource>()
        val sourceCodes=rows("sources").mapNotNull{it.publishedString("code")}.distinct()
        val queryVerse=passage(code,query)?.takeIf{it.verseId==verseId}
        val queryText=queryVerse?.text
        for(sourceCode in sourceCodes) {
            val map=verifier.selectedMap(sourceCode,code)
            val mapId=map?.number("id")
            val initial=ReferenceResolution(sourceProfile=verifier.profile("reference_source",sourceCode),editionProfile=verifier.profile("translation",code),mapId=mapId,mapVersion=map?.publishedString("version"),mapSha256=map?.publishedString("sha256"))
            if(mapId==null) {
                val group=rows("references",query).filter{it.publishedString("source_code")==sourceCode}
                references+=group.map{legacy(it,sourceCode).copy(resolution=initial)}
                admissions+=ReferenceQuerySource(sourceCode,"unknown",if(group.isEmpty())emptyList()else listOf(query))
                continue
            }
            val incoming=inverse(mapId,query)
            val originals=incoming.mapNotNull{it.publishedString("source_osis_ref")}.distinct()
            var admissionStatus=if(originals.size>1||incoming.any{it.publishedString("relation")=="ambiguous"})"ambiguous" else "raw"
            if(originals.isEmpty()) {
                // Keep the raw stored rows for audit; no remapped source is invented.
                references+=rows("references",query).filter{it.publishedString("source_code")==sourceCode}.map{legacy(it,sourceCode).copy(resolution=initial.copy(status="raw"))}
            }
            for(original in originals) {
                val group=rows("references",original).filter{it.publishedString("source_code")==sourceCode}
                val sourceEntries=forward(mapId,original)
                val sourceEntry=sourceEntries.singleOrNull()?.takeIf{it.publishedString("target_osis_ref")==query}
                val groupDigest=studySourceDigest(sourceCode,original,group)
                val sourceMoved=original!=query
                val sourceExact=incoming.size==1&&sourceEntries.size==1&&sourceEntry?.exactEntry()==true&&reciprocal(sourceEntry,incoming.single())
                val sourceHashes=sourceEntry!=null&&queryText!=null&&
                    sourceEntry.digestMatches("source_reference_sha256",groupDigest,sourceMoved||sourceEntry.hasContentFingerprint())&&
                    sourceEntry.digestMatches("target_content_sha256",studyTextDigest(queryText),sourceMoved)
                if(sourceExact&&sourceHashes)admissionStatus="verified"
                val sourceResolved=sourceEntries.map{resolved(it,if(sourceEntries.size>1)"one-to-many" else if(incoming.size>1)"many-to-one" else "one-to-one")}
                for(row in group) {
                    val item=legacy(row,sourceCode)
                    val targetOriginal=item.target.osisRef
                    val targets=forward(mapId,targetOriginal)
                    val outputs=mutableListOf<ResolvedReference>()
                    var targetExact=targets.size==1
                    for(entry in targets) {
                        val targetOsis=entry.getValue("target_osis_ref").jsonPrimitive.content
                        val reverse=inverse(mapId,targetOsis)
                        val target=passage(code,targetOsis)
                        val targetText=target?.text
                        val cardinality=if(targets.size>1)"one-to-many" else if(reverse.size>1)"many-to-one" else "one-to-one"
                        outputs+=resolved(entry,cardinality,target)
                        val moved=sourceMoved||targetOriginal!=targetOsis
                        val sourceGuard=sourceEntry!=null&&queryText!=null&&
                            sourceEntry.digestMatches("source_reference_sha256",groupDigest,moved||sourceEntry.hasContentFingerprint())&&
                            sourceEntry.digestMatches("target_content_sha256",studyTextDigest(queryText),moved)
                        val targetGuard=targetText!=null&&entry.digestMatches("target_content_sha256",studyTextDigest(targetText),moved)
                        targetExact=targetExact&&entry.exactEntry()&&reverse.size==1&&reciprocal(entry,reverse.single())&&sourceGuard&&targetGuard
                    }
                    val ambiguous=admissionStatus=="ambiguous"||sourceEntries.size>1||targets.size>1||outputs.any{it.relation=="ambiguous"||it.cardinality!="one-to-one"}
                    val status=if(ambiguous)"ambiguous" else if(sourceExact&&sourceHashes&&targetExact&&outputs.size==1)"verified" else "raw"
                    references+=item.copy(resolvedSource=sourceResolved,resolvedTargets=outputs,resolution=initial.copy(status=status))
                }
            }
            admissions+=ReferenceQuerySource(sourceCode,admissionStatus,originals,mapId,initial.mapVersion,initial.mapSha256)
        }
        return CrossReferences(StudyVerse(verseId,query),code,references,ReferenceQueryResolution(2,query,admissions)).guardedReferences()
    }
}
