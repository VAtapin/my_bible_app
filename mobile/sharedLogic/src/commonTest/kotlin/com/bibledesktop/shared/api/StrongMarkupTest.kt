package com.bibledesktop.shared.api
import kotlin.test.*
class StrongMarkupTest {
    @Test fun remainingAttachedMarkersKeepUtf16AnchorsAndOrdinaryNumbers() {
        val verse= BibleVerse(1,1,"Mark.1.38","G2532Он говорит G3004", "G2532Он говорит 12 людям",hasStrongMarkup=true)
        assertEquals(listOf(RemainingStrongRange(0,5,"G2532")),verse.remainingStrongRanges())
        assertEquals(emptyList(),verse.copy(hasStrongMarkup=false).remainingStrongRanges())
        assertEquals(emptyList(),verse.copy(plainText="XH430 G250000 H0 12").remainingStrongRanges())
    }
    @Test fun explicitPrefixesArePreservedWithoutInventingWordBindings() { assertEquals(listOf("H430", "G25"), sourceStrongNumbers("Бог H0430 <S>G25</S> H430", true)); assertEquals(emptyList(), sourceStrongNumbers("430 G250000 XH430 H0", true)); assertEquals(emptyList(), sourceStrongNumbers("H430", false)) }
    @Test fun executableTextAndHtmlAttributesDoNotCreateIdentifiers() { assertEquals(listOf("H430"), sourceStrongNumbers("<script>H1</script><span title=\"H2\">Бог</span> <S>H430</S>", true)) }
}
