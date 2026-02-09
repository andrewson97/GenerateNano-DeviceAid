package com.example.llama

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.example.llama.ui.theme.LlamaAndroidTheme
import java.io.File

class MainActivity(
    activityManager: ActivityManager? = null,
    downloadManager: DownloadManager? = null,
    clipboardManager: ClipboardManager? = null,
): ComponentActivity() {
    private val tag: String? = this::class.simpleName

    private val activityManager by lazy { activityManager ?: getSystemService<ActivityManager>()!! }
    private val downloadManager by lazy { downloadManager ?: getSystemService<DownloadManager>()!! }
    private val clipboardManager by lazy { clipboardManager ?: getSystemService<ClipboardManager>()!! }

    private val viewModel: MainViewModel by viewModels()

    // Get a MemoryInfo object for the device's current memory status.
    private fun availableMemory(): ActivityManager.MemoryInfo {
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StrictMode.setVmPolicy(
            VmPolicy.Builder(StrictMode.getVmPolicy())
                .detectLeakedClosableObjects()
                .build()
        )

        val free = Formatter.formatFileSize(this, availableMemory().availMem)
        val total = Formatter.formatFileSize(this, availableMemory().totalMem)

        viewModel.log("Current memory: $free / $total")
        viewModel.log("Downloads directory: ${getExternalFilesDir(null)}")

        val extFilesDir = getExternalFilesDir(null)

        // 🎯 Copy InterPlayThink model from assets to files directory on first launch
        val modelFile = File(extFilesDir, "InterplayThink.gguf")
        if (!modelFile.exists()) {
            viewModel.log("📦 Copying InterPlayThink model from assets...")
            Thread {
                try {
                    assets.open("models/InterplayThink.gguf").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    runOnUiThread {
                        viewModel.log("✅ Model copied: ${modelFile.length() / (1024 * 1024)}MB")
                        viewModel.log("🎯 Auto-loading InterPlayThink model...")
                        viewModel.load(modelFile.absolutePath)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        viewModel.log("❌ Failed to copy model: ${e.message}")
                    }
                }
            }.start()
        } else {
            viewModel.log("✅ Model found: ${modelFile.length() / (1024 * 1024)}MB")
            viewModel.log("🎯 Auto-loading InterPlayThink model...")
            viewModel.load(modelFile.absolutePath)
        }

        // 🎯 Use InterPlayThink model from project directory
        val models = listOf(
            Downloadable(
                "🎯 InterPlayThink-3B (Local - Already Loaded)",
                Uri.EMPTY, // Not used since model is already loaded
                modelFile,
            )
        )

        setContent {
            LlamaAndroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainCompose(
                        viewModel,
                        clipboardManager,
                        downloadManager,
                        models,
                    )
                }

            }
        }
    }
}

@Composable
fun MainCompose(
    viewModel: MainViewModel,
    clipboard: ClipboardManager,
    dm: DownloadManager,
    models: List<Downloadable>
) {
    Column {
        val scrollState = rememberLazyListState()

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(state = scrollState) {
                items(viewModel.messages) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        OutlinedTextField(
            value = viewModel.message,
            onValueChange = { viewModel.updateMessage(it) },
            label = { Text("Message") },
        )
        Row {
            Button({ viewModel.send() }) { Text("Send") }
            Button({ viewModel.bench(8, 4, 1) }) { Text("Bench") }
            Button({ viewModel.clear() }) { Text("Clear") }
            Button({
                viewModel.messages.joinToString("\n").let {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", it))
                }
            }) { Text("Copy") }
        }
        
        Row {
            Button({ viewModel.getGpuInfo() }) { Text("🎮 GPU Info") }
            Button({ viewModel.configureGpu(32) }) { Text("⚡ Enable GPU (32)") }
            Button({ viewModel.configureGpu(0) }) { Text("🔴 CPU Only") }
        }

        Column {
            for (model in models) {
                Downloadable.Button(viewModel, dm, model)
            }
        }
    }
}
