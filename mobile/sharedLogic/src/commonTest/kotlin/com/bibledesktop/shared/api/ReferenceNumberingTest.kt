package com.bibledesktop.shared.api
import kotlin.test.*
class ReferenceNumberingTest {
 private val target=ReferenceTarget(2,"Joel.3.2","Joel 3:2","joel",3,2,"Cached possibly mismatched text")
 @Test fun legacyPackagesDefaultUnknownAndHideCachedText(){val groups=referenceGroups(listOf(CrossReference(1,target)));assertNull(groups.first().targets.first().text);assertEquals("unknown",groups.first().targets.first().versification.status)}
 @Test fun rawAndAmbiguousKeepProvenanceWithoutPreview(){listOf("raw","ambiguous").forEach{status->val group=referenceGroups(listOf(CrossReference(1,target,versification=ReferenceVersification(status,"MT","LXX","v1")))).first();assertNull(group.targets.first().text);assertEquals("MT",group.targets.first().versification.sourceProfile)}}
 @Test fun verifiedPreviewRequiresSourceEditionAndMappingVersion(){assertFalse(ReferenceVersification("verified","MT","MT",null).verified);val group=referenceGroups(listOf(CrossReference(1,target,versification=ReferenceVersification("verified","MT","MT","v1")))).first();assertEquals(target.text,group.targets.first().text)}
}
