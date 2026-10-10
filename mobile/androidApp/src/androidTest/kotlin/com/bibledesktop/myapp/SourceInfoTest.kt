package com.bibledesktop.myapp
import com.bibledesktop.myapp.ui.study.publishedSourceFields
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
class SourceInfoTest {
 @Test fun unknownSourceFieldsAreNotInferredFromCode(){val metadata=Json.parseToJsonElement("""{"code":"RU-2026","name":"Published","language_code":"ru","author":123,"updated_at":null}""").jsonObject;val fields=publishedSourceFields(metadata,"verified-SHA");assertEquals("Published",fields[0]);assertEquals("ru",fields[2]);assertNull(fields[3]);assertNull(fields[4]);assertNull(fields[5]);assertEquals("verified-SHA",fields[6]);assertNull(fields[7])}
 @Test fun sourceMarkupIsShownAsPlainText(){val metadata=Json.parseToJsonElement("""{"name":"<i>A &amp; B</i>","author":"<script>bad()</script>"}""").jsonObject;val fields=publishedSourceFields(metadata);assertEquals("A & B",fields[0]);assertNull(fields[3])}
}
