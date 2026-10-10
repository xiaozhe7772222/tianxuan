package top.wkbin.tianxuan.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.core.database.KbDocumentEntity
import top.wkbin.tianxuan.harness.knowledge.KnowledgeHit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onBack: () -> Unit,
    viewModel: KnowledgeViewModel = koinViewModel(),
) {
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val error by viewModel.error.collectAsState()
    val adding by viewModel.adding.collectAsState()
    val reEmbedding by viewModel.reEmbedding.collectAsState()
    val embeddingModel by viewModel.embeddingModel.collectAsState()
    val embeddingEndpointSuffix by viewModel.embeddingEndpointSuffix.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<KbDocumentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加文档")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchSection(
                query = searchQuery,
                onQueryChange = viewModel::search,
                results = searchResults,
                searching = searching,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            EmbeddingSettingsCard(
                embeddingModel = embeddingModel,
                embeddingEndpointSuffix = embeddingEndpointSuffix,
                onModelChange = viewModel::setEmbeddingModel,
                onSuffixChange = viewModel::setEmbeddingEndpointSuffix,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Text(
                "文档列表 (${documents.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (documents.isEmpty()) {
                EmptyState(modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    documents.forEach { doc ->
                        DocumentCard(
                            doc = doc,
                            onDelete = { showDeleteConfirm = doc },
                            onReEmbed = { viewModel.reEmbed(doc.id) },
                            isReEmbedding = reEmbedding == doc.id,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAddDialog) {
        AddDocumentDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, content ->
                viewModel.addDocument(name, content)
                showAddDialog = false
            },
            isLoading = adding,
        )
    }

    showDeleteConfirm?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除文档") },
            text = { Text("确定要删除「${doc.name}」吗？共 ${doc.chunkCount} 个分块将一并移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeDocument(doc.id)
                        showDeleteConfirm = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            },
        )
    }

    error?.let { msg ->
        Snackbar(
            action = {
                TextButton(onClick = { viewModel.clearError() }) { Text("关闭") }
            },
        ) { Text(msg) }
    }
}

@Composable
private fun SearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<KnowledgeHit>,
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("检索测试", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入内容检索相关文档片段…") },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = "检索")
                    }
                },
                singleLine = true,
            )
            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                results.forEach { hit ->
                    HitCard(hit)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: KnowledgeHit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    hit.docName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Badge { Text("%.2f".format(hit.score)) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                hit.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    doc: KbDocumentEntity,
    onDelete: () -> Unit,
    onReEmbed: () -> Unit,
    isReEmbedding: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(doc.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "来源: ${doc.source}  |  分块: ${doc.chunkCount}  |  添加于 ${formatTime(doc.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!doc.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    doc.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onReEmbed) {
                    if (isReEmbedding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新嵌入")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (text, color) = when (status) {
        "embedded" -> "已嵌入" to MaterialTheme.colorScheme.primary
        "embedding" -> "嵌入中…" to MaterialTheme.colorScheme.tertiary
        "partial" -> "部分嵌入" to MaterialTheme.colorScheme.secondary
        "error" -> "错误" to MaterialTheme.colorScheme.error
        else -> "待处理" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Badge(containerColor = color) { Text(text) }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "还没有知识库文档",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "点击右上角 + 添加文档。添加后系统会自动调用嵌入 API 生成向量，检索时按语义相似度匹配相关内容注入对话上下文。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddDocumentDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, content: String) -> Unit,
    isLoading: Boolean,
) = AddDocumentDialogImpl(onDismiss, onAdd, isLoading)

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
