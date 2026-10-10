package top.wkbin.tianxuan.ui.knowledge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 嵌入配置卡片：模型名与端点后缀（适配不支持默认 text-embedding-3-small 的提供商）。 */
@Composable
fun EmbeddingSettingsCard(
    embeddingModel: String,
    embeddingEndpointSuffix: String,
    onModelChange: (String) -> Unit,
    onSuffixChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("嵌入配置", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = embeddingModel,
                onValueChange = onModelChange,
                label = { Text("嵌入模型名") },
                placeholder = { Text("text-embedding-3-small") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = embeddingEndpointSuffix,
                onValueChange = onSuffixChange,
                label = { Text("嵌入端点后缀") },
                placeholder = { Text("/embeddings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "嵌入请求走激活模型档案的 Base URL + Key + 此模型名与端点后缀。" +
                    "若 404/NOT_FOUND，说明你的提供商不提供此端点或模型名不对，请改为实际支持的名字。" +
                    "嵌入失败时自动降级为关键词检索（BM25），知识库仍可用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
