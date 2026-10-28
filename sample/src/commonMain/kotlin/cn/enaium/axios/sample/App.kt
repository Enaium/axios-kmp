package cn.enaium.axios.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Brand = Color(0xFF4459F5)

@Composable
fun App() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            secondary = Color(0xFF00A98F),
            surfaceVariant = Color(0xFFF2F4FF),
        )
    ) {
        val playground = remember { AxiosPlayground() }
        DisposableEffect(Unit) {
            onDispose { playground.close() }
        }

        var result by remember { mutableStateOf<DemoResult?>(null) }
        var running by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        fun runDemo(label: String, block: suspend () -> DemoResult) {
            if (running) return
            selected = label
            scope.launch {
                running = true
                try {
                    result = block()
                } finally {
                    running = false
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = "Axios KMP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Brand,
                )
                Text(
                    text = "Kotlin Multiplatform port of axios with Ktor HttpClient as the default adapter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Examples",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        enabled = !running,
                        onClick = {
                            runDemo("Overview") { playground.fetchOverview() }
                        },
                    ) {
                        Text("Overview")
                    }
                    playground.actions.forEach { action ->
                        Button(
                            enabled = !running,
                            onClick = { runDemo(action.label) { action.run() } },
                        ) {
                            Text(action.label)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = playground.actions.firstOrNull { it.label == selected }?.description
                        ?: selected?.let { "Request headers observed by httpbin, including interceptor headers." }
                        ?: "Choose an example to execute a real request. Network access is required for httpbin.org and jsonplaceholder.typicode.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (running) {
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Running...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                result?.let { demo ->
                    Spacer(Modifier.height(20.dp))
                    ResultCard(demo)
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: DemoResult) {
    val failed = result.errorCode != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) Color(0xFFFFF1F0) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                result.status?.let { status ->
                    Text(
                        text = buildString {
                            append(status)
                            result.statusText?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (failed) MaterialTheme.colorScheme.error else Brand,
                    )
                }
            }

            result.durationMs?.let { duration ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${duration} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            result.errorCode?.let { code ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            result.errorMessage?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(14.dp))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.height(14.dp))

            SelectionContainer {
                Text(
                    text = result.body.ifBlank { "(empty body)" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
