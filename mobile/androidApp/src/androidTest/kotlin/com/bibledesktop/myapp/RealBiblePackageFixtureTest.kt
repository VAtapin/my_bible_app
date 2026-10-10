package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Explicit debug-only integration preparation; reuses and audits genuine downloaded records. */
class RealBiblePackageFixtureTest {
 @Test fun seedInstalledRussianStrongAndKingJamesForReaderIntegration() = runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  check(context.packageName=="com.bibledesktop.myapp.debug")
  Assume.assumeTrue("Opt in with seedInstalledBibles=true before link/smoke integration",InstrumentationRegistry.getArguments().getString("seedInstalledBibles")=="true")
  val api=BibleApiClient();val store=OfflineStore(context)
  try{
   // Install the genuine APK snapshot before auditing its records and explicit unavailable chapters.
   BundledBible.install(context,store)
   for(code in listOf("BQ_RUSSIAN_RST_STRONG","BQ_ENGLISH_KJV_1769")){
   var last=-1
   // Keep the existing request pause; downloader audits saved chapters even for installed packages.
   assertTrue(BibleDownloadEngine(api,store).download(code){pack->if(pack.done/100!=last){last=pack.done/100;android.util.Log.i("ReaderFixtureSeed","$code ${pack.done}/${pack.total}")}})
   val pack=store.read(biblePackageKey(code),BiblePackage.serializer())?:error("Missing real package metadata")
   assertEquals(code,pack.translation.code);assertTrue(pack.isInstalled)
   for(book in pack.books)for(number in 1..book.chaptersCount){val chapter=store.read(chapterKey(code,book.slug,number),BibleChapter.serializer());if(chapter!=null){assertEquals(code,chapter.translation.code);assertEquals(book.slug,chapter.book.slug);assertEquals(number,chapter.chapter.number)}else assertTrue("Unreported unavailable chapter $code/${book.slug}/$number", "${book.name} $number" in pack.unavailable)}
  }}finally{api.close()}
 }
}
