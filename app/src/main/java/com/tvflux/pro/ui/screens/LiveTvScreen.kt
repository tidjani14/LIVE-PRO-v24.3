package com.tvflux.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvflux.pro.data.db.entity.ChannelEntity
import com.tvflux.pro.ui.CatalogViewModel

@Composable
fun LiveTvScreen(
    viewModel: CatalogViewModel,
    onChannelClick: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val channels by viewModel.liveTv.collectAsStateWithLifecycle()
    val uiState by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF000000),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF000000))
        ) {
            // شريط علوي غير حاجب يظهر أثناء عمل Phase B (تحميل VOD/Series في الخلفية)
            if (uiState.isSyncingBackgroundVod) {
                BackgroundSyncBanner(
                    vodCount = uiState.vodLoadedProgress,
                    seriesCount = uiState.seriesLoadedProgress
                )
            }

            when {
                uiState.isBooting && channels.isEmpty() -> {
                    LoadingSpinnerView(isSyncingLive = uiState.isSyncingLive)
                }
                channels.isEmpty() -> {
                    EmptyChannelsStateView(
                        errorMessage = uiState.errorMessage,
                        onRetry = { viewModel.manualRefresh() }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = channels,
                            key = { channel -> channel.streamId },
                            contentType = { channel -> channel.contentType }
                        ) { channel ->
                            LiveChannelRowItem(
                                channel = channel,
                                onClick = { onChannelClick(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundSyncBanner(
    vodCount: Int,
    seriesCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141824))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFD4AF37)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "⚡ جارٍ تحديث مكتبة الأفلام ($vodCount) والمسلسلات ($seriesCount) في الخلفية...",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        LinearProgressIndicator(
            modifier = Modifier
                .width(96.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFFD4AF37),
            trackColor = Color(0xFF223039)
        )
    }
}

@Composable
private fun LoadingSpinnerView(isSyncingLive: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = Color(0xFFD4AF37),
                strokeWidth = 3.5.dp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isSyncingLive) {
                    "⚡ جارٍ سحب القنوات المباشرة بأولوية قصوى..."
                } else {
                    "⚡ جارٍ فتح المكتبة المحلية..."
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyChannelsStateView(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage ?: "لا توجد قنوات متاحة في هذه القائمة حالياً",
                color = Color(0xFFE2E8F0),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(text = "تحديث القائمة الآن 🔄", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LiveChannelRowItem(
    channel: ChannelEntity,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF223039) else Color(0xFF141D23))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFFD4AF37) else Color(0xFF222226),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${channel.number}",
                color = Color(0xFFD4AF37),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = if (isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.categoryName.isNotBlank()) {
                    Text(
                        text = channel.categoryName,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
