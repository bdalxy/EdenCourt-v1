package com.bdalxy.edencourt.feature.worldbook

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.WorldInfoDao
import com.bdalxy.edencourt.data.local.entity.WorldInfoBook
import com.bdalxy.edencourt.domain.TokenEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** 世界书列表项：书 + 条目数 + 全书条目 token 估算（管理页展示，T6.3 关注内存不误算大文本） */
data class WorldBookItem(
    val book: WorldInfoBook,
    val entryCount: Int,
    val tokens: Int,
)

/**
 * 世界书列表页 ViewModel（C 阶段，v1.5-A3）。
 * 观察全部世界书，并为每本聚合「条目数 / 全书 token 估算」，供列表卡片展示。
 */
class WorldBookViewModel(
    private val dao: WorldInfoDao,
) : ViewModel() {

    /** 世界书列表（含聚合计数与 token）。读 DB 放 IO，避免主线程阻塞 */
    private val _books = dao.observeBooks()
        .flatMapLatest { books ->
            flow {
                val items = withContext(Dispatchers.IO) {
                    books.map { b -> WorldBookItem(b, dao.countEntries(b.id), estimateBookTokens(b.id)) }
                }
                emit(items)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 世界书列表（含聚合计数与 token）。stateIn 已产出 StateFlow，此处作只读别名暴露给 UI。
     */
    val books: StateFlow<List<WorldBookItem>> = _books

    /** 估算某本世界书所有已启用条目的 token 合计（1 字符≈1 token，UI 标「估」） */
    private fun estimateBookTokens(bookId: Long): Int =
        dao.getEntriesByBook(bookId).filter { it.enabled }.sumOf { TokenEstimator.count(it.content) }

    companion object {
        fun Factory(app: EdenCourtApp) = viewModelFactory {
            initializer {
                WorldBookViewModel(app.worldInfoDao)
            }
        }
    }
}

/**
 * 世界书管理页（C 阶段：书列表 + 新建入口）。
 * - 空态提示 + 「+ 新建世界书」卡片
 * - 每本书卡片：书名 + 简介 + 条目数 + token 估算；点击进入该书编辑页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
) {
    val app = LocalContext.current.applicationContext as EdenCourtApp
    val viewModel: WorldBookViewModel = viewModel(factory = WorldBookViewModel.Factory(app))
    val books by viewModel.books.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("世界书") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 新建世界书入口
            item {
                Card(
                    onClick = { onOpenBook(0L) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "新建世界书",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (books.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "还没有世界书\n点上方「新建世界书」创建第一本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                items(books, key = { it.book.id }) { item ->
                    BookCard(item = item, onClick = { onOpenBook(item.book.id) })
                }
            }
        }
    }
}

/** 世界书单本卡片：书名 + 简介 + 条目数 + token 估算 */
@Composable
private fun BookCard(item: WorldBookItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.book.name.ifBlank { "未命名世界书" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (item.book.description.isNotBlank()) {
                    Text(
                        text = item.book.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "${item.entryCount} 条目 · ~${item.tokens} tok",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}