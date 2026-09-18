package com.bibledesktop.myapp.ui.more

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.bible.BookmarkEntry
import com.bibledesktop.myapp.ui.bible.BookmarkStore
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.myapp.ui.theme.PrimaryBlue
import java.util.Locale

@Composable
fun MoreScreen(
    language: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenBookmark: (BookmarkEntry) -> Unit,
) {
    val context = LocalContext.current
    var bookmarks by remember { mutableStateOf(BookmarkStore.load(context)) }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Navy)
            }
            Text(
                localText(R.string.more_title, language),
                modifier = Modifier.weight(1f).padding(end = 48.dp),
                color = Ink,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingsCard(language, onSettings)
                Text(
                    localText(R.string.bookmarks_title, language),
                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                    color = Navy,
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (bookmarks.isEmpty()) {
                item {
                    Text(
                        localText(R.string.bookmarks_empty, language),
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = PrimaryBlue,
                    )
                }
            } else {
                items(bookmarks, key = { "${it.translationCode}:${it.reference}" }) { bookmark ->
                    BookmarkCard(
                        language = language,
                        bookmark = bookmark,
                        onOpen = { onOpenBookmark(bookmark) },
                        onShare = { share(context, bookmark) },
                        onDelete = { bookmarks = BookmarkStore.remove(context, bookmark) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(language: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Navy),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).background(LightBlue, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = Navy)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(localText(R.string.settings_title, language), color = Color.White, fontWeight = FontWeight.Bold)
                Text(localText(R.string.settings_subtitle, language), color = LightBlue, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun BookmarkCard(
    language: String,
    bookmark: BookmarkEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = Navy)
                Text(
                    "${bookmark.bookName} ${bookmark.chapter}:${bookmark.verse}",
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    color = Navy,
                    fontWeight = FontWeight.Bold,
                )
                Text(bookmark.translationName, color = PrimaryBlue, fontSize = 11.sp)
            }
            Text(
                bookmark.text,
                modifier = Modifier.padding(top = 10.dp),
                color = Ink,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                maxLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, localText(R.string.bible_share, language), tint = PrimaryBlue)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, localText(R.string.bookmark_delete, language), tint = PrimaryBlue)
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, localText(R.string.bookmark_open, language), tint = Navy)
                }
            }
        }
    }
}

private fun share(context: Context, bookmark: BookmarkEntry) {
    val message = "${bookmark.text}\n\n${bookmark.bookName} ${bookmark.chapter}:${bookmark.verse} · ${bookmark.translationName}"
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            },
            null,
        ),
    )
}

@Composable
private fun localText(@StringRes id: Int, language: String, vararg args: Any): String {
    val context = LocalContext.current
    return remember(id, language, args.toList()) {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        context.createConfigurationContext(configuration).resources.getString(id, *args)
    }
}
