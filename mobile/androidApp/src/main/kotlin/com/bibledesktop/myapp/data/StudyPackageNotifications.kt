package com.bibledesktop.myapp.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.bibledesktop.myapp.R
import java.util.UUID

private fun studyDownloadLabels(context:Context):List<String>{val language=context.getSharedPreferences("bible-desktop-native-profile",Context.MODE_PRIVATE).getString("uiLanguage","ru");return when(language){"ru"->listOf("Материалы офлайн","Загрузка","Проверка и установка","Отменить");"de"->listOf("Offline-Materialien","Download","Prüfung und Installation","Abbrechen");"uk"->listOf("Матеріали офлайн","Завантаження","Перевірка та встановлення","Скасувати");else->listOf("Offline materials","Downloading","Verifying and installing","Cancel")}}
internal fun studyPackageForeground(context:Context,workId:UUID,pack:StudyOfflinePackage,done:Long,total:Long,verifying:Boolean):ForegroundInfo{
    val labels=studyDownloadLabels(context);val channel="study-full-modules-v1"
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,labels[0],NotificationManager.IMPORTANCE_LOW))
    val notification=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_reminder).setContentTitle(labels[0]).setContentText(labels[if(verifying)2 else 1]+": "+pack.id)
        .setOngoing(true).setOnlyAlertOnce(true).setProgress(1000,if(total>0)(done.toDouble()/total*1000).toInt().coerceIn(0,1000)else 0,verifying||total<=0)
        .addAction(0,labels[3],WorkManager.getInstance(context).createCancelPendingIntent(workId)).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
    val id=workId.hashCode().and(0x7fffffff)
    return if(Build.VERSION.SDK_INT>=29)ForegroundInfo(id,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)else ForegroundInfo(id,notification)
}
