package com.bibledesktop.shared.api
import kotlin.test.*
class StrongMarkupTest {
    @Test fun explicitPrefixesArePreservedWithoutInventingWordBindings() { assertEquals(listOf("H430", "G25"), sourceStrongNumbers("Бог H0430 <S>G25</S> H430", true)); assertEquals(emptyList(), sourceStrongNumbers("430 G250000 XH430 H0", true)); assertEquals(emptyList(), sourceStrongNumbers("H430", false)) }
    @Test fun executableTextAndHtmlAttributesDoNotCreateIdentifiers() { assertEquals(listOf("H430"), sourceStrongNumbers("<script>H1</script><span title=\"H2\">Бог</span> <S>H430</S>", true)) }
}
