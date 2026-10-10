package com.bibledesktop.myapp.data

import android.graphics.BitmapFactory
import java.io.File
import java.io.RandomAccessFile

/** Reject cached HTML/header-only/truncated image responses before treating a map as available. */
internal fun isReadableDictionaryImage(file:File):Boolean=runCatching {
    require(file.isFile&&file.length() in 12..20*1024*1024L)
    RandomAccessFile(file,"r").use { input ->
        val header=ByteArray(12);input.readFully(header)
        val complete=when {
            header[0]==0xff.toByte()&&header[1]==0xd8.toByte()->{input.seek(input.length()-2);input.readUnsignedShort()==0xffd9}
            header.copyOfRange(0,8).contentEquals(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a))->{
                input.seek(input.length()-12);val tail=ByteArray(12);input.readFully(tail)
                tail.contentEquals(byteArrayOf(0,0,0,0,0x49,0x45,0x4e,0x44,0xae.toByte(),0x42,0x60,0x82.toByte()))
            }
            String(header,0,6,Charsets.US_ASCII) in listOf("GIF87a","GIF89a")->{
                // A global palette promised by the header must actually be present.
                input.seek(10);val packed=input.readUnsignedByte();val palette=if(packed and 0x80!=0)3L*(1L shl ((packed and 7)+1))else 0L
                input.seek(input.length()-1);input.length()>13+palette&&input.readUnsignedByte()==0x3b
            }
            String(header,0,4,Charsets.US_ASCII)=="RIFF"&&String(header,8,4,Charsets.US_ASCII)=="WEBP"->{
                val length=(4..7).foldIndexed(0L){index,acc,n->acc or ((header[n].toLong() and 255) shl (8*index))};length+8==input.length()
            }
            else->false
        }
        require(complete)
    }
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
    require(bounds.outWidth in 1..40000&&bounds.outHeight in 1..40000)
    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2
    val bitmap=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Image cannot be decoded")
    bitmap.recycle();true
}.getOrDefault(false)
