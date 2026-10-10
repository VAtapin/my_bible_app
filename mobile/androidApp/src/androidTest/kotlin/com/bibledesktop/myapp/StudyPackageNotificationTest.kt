package com.bibledesktop.myapp
import android.app.Notification
import android.content.pm.ServiceInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.data.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
class StudyPackageNotificationTest {
 @Test fun foregroundShowsActualDownloadProgressAndCancelablePublicModuleOnly(){val context=InstrumentationRegistry.getInstrumentation().targetContext;val pack=StudyOfflinePackage("PUBLIC_MODULE","dictionary","a".repeat(64),1000,"a".repeat(64),"/api/offline/packages/PUBLIC_MODULE");val info=studyPackageForeground(context,UUID.randomUUID(),pack,500,1000,false);assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,info.foregroundServiceType);assertEquals(500,info.notification.extras.getInt(Notification.EXTRA_PROGRESS));assertTrue(info.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("PUBLIC_MODULE"));assertEquals(1,info.notification.actions.size);assertNotNull(info.notification.actions[0].actionIntent);assertTrue(info.notification.flags and Notification.FLAG_ONGOING_EVENT!=0)}
}
