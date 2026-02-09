# RAG Usage Guide - Adreno Q&A SDK

Complete guide for implementing Retrieval-Augmented Generation in your Android application.

---

## 📋 Prerequisites

### 1. SDK Assets (Already Included ✅)

The SDK already includes:
```
sdk/src/main/assets/models/
├── InterplayGTE.onnx      # GTE embedding model (~22MB) ✅
└── bert_tokenizer.json    # BERT tokenizer (~695KB) ✅
```

### 2. Application Assets (You Provide)

Place these files in **your app's** `assets` directory:

```
app/src/main/assets/
├── docs/
│   └── Input_QA.json          # Your Q&A document (see format below)
└── llm/
    └── Qwen3-1.7B-Q4_0.gguf   # LLM model
```

**Important:** The models are bundled in the SDK. You only need to provide your documents and LLM model.

### 2. Input_QA.json Format

```json
[
  {
    "page": 0,
    "qa_pairs": [
      {
        "question": "What is the product warranty?",
        "answer": "The product comes with a 2-year warranty covering manufacturing defects.",
        "image_refs": []
      },
      {
        "question": "How do I reset the device?",
        "answer": "Press and hold the reset button for 5 seconds until the LED blinks.",
        "image_refs": ["images/reset_button.png"]
      }
    ]
  },
  {
    "page": 1,
    "qa_pairs": [
      {
        "question": "What is the maximum speed?",
        "answer": "The maximum speed is 100 km/h as specified in the technical documentation.",
        "image_refs": []
      }
    ]
  }
]
```

---

## 🚀 Method 1: Manual RAG (Full Control)

### Step 1: Build the Index (One-time setup)

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Build RAG index in background
        Thread {
            buildRAGIndex()
        }.start()
    }
    
    private fun buildRAGIndex() {
        val ragManager = RAGContextManager.getInstance()
        
        val outputPath = File(filesDir, "document_embeddings.json").absolutePath
        
        val success = ragManager.buildIndex(
            inputJsonPath = "docs/Input_QA.json",
            modelPath = "models/gte-tiny.onnx",
            tokenizerPath = "models/bert_tokenizer.json",
            outputPath = outputPath
        )
        
        if (success) {
            Log.i("RAG", "✅ Index built successfully!")
            initializeRAG()
        } else {
            Log.e("RAG", "❌ Failed to build index")
        }
    }
    
    private fun initializeRAG() {
        val ragManager = RAGContextManager.getInstance()
        
        val indexPath = File(filesDir, "document_embeddings.json").absolutePath
        
        val success = ragManager.initialize(
            context = applicationContext,
            modelPath = "models/gte-tiny.onnx",
            tokenizerPath = "models/bert_tokenizer.json",
            indexPath = indexPath
        )
        
        if (success) {
            Log.i("RAG", "✅ RAG initialized and ready!")
        }
    }
}
```

### Step 2: Use RAG with SDK

```kotlin
class ChatActivity : AppCompatActivity() {
    
    private lateinit var sdk: AdrenoMenuSDK
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SDK
        sdk = AdrenoMenuSDK.initialize(
            context = applicationContext,
            modelPath = "llm/Qwen3-1.7B-Q4_0.gguf",
            systemPrompt = "You are a helpful assistant. Answer based on the provided context.",
            gpuLayers = 27
        )
        
        // Enable RAG
        SystemPromptManagerEnhanced.getInstance().enableRAG()
    }
    
    private fun askQuestion(userQuery: String) {
        Thread {
            // 1. Search for relevant documents
            val ragManager = RAGContextManager.getInstance()
            val searchResults = ragManager.search(userQuery, topK = 5)
            
            if (searchResults != null && searchResults.isNotEmpty()) {
                Log.i("RAG", "Found ${searchResults.size} relevant documents")
                
                // 2. Inject RAG context
                SystemPromptManagerEnhanced.getInstance().setRAGContext(searchResults)
                
                // 3. Refresh cache with new context
                sdk.refreshCache()
            }
            
            // 4. Generate response (RAG context is automatically included)
            sdk.generateResponse(userQuery, maxTokens = 512) { token ->
                runOnUiThread {
                    appendToChat(token)
                }
            }
        }.start()
    }
}
```

---

## 🎯 Method 2: Auto RAG (Simplified)

Automatically search and inject context for every query:

```kotlin
class AutoRAGActivity : AppCompatActivity() {
    
    private lateinit var sdk: AdrenoMenuSDK
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SDK
        sdk = AdrenoMenuSDK.initialize(
            context = applicationContext,
            modelPath = "llm/Qwen3-1.7B-Q4_0.gguf",
            systemPrompt = "You are a helpful assistant. Answer based on the provided context.",
            gpuLayers = 27
        )
        
        // Enable RAG
        SystemPromptManagerEnhanced.getInstance().enableRAG()
        
        // Initialize RAG
        val ragManager = RAGContextManager.getInstance()
        val indexPath = File(filesDir, "document_embeddings.json").absolutePath
        ragManager.initialize(
            applicationContext,
            "models/gte-tiny.onnx",
            "models/bert_tokenizer.json",
            indexPath
        )
    }
    
    private fun askWithAutoRAG(userQuery: String) {
        Thread {
            // Automatic RAG retrieval
            val context = RAGContextManager.getInstance().searchAndGetContext(userQuery, 5)
            
            if (context.isNotEmpty()) {
                SystemPromptManagerEnhanced.getInstance().setRAGContextDirect(context)
                sdk.refreshCache()
            }
            
            // Generate response
            sdk.generateResponse(userQuery) { token ->
                runOnUiThread {
                    appendToChat(token)
                }
            }
        }.start()
    }
}
```

---

## 📱 Complete Example: Q&A App

```kotlin
class QAApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Build index on first launch
        if (!isIndexBuilt()) {
            Thread {
                buildAndInitializeRAG()
            }.start()
        }
    }
    
    private fun buildAndInitializeRAG() {
        val ragManager = RAGContextManager.getInstance()
        val outputPath = File(filesDir, "document_embeddings.json").absolutePath
        
        // Build index
        val built = ragManager.buildIndex(
            "docs/Input_QA.json",
            "models/gte-tiny.onnx",
            "models/bert_tokenizer.json",
            outputPath
        )
        
        if (built) {
            // Initialize for use
            ragManager.initialize(
                applicationContext,
                "models/gte-tiny.onnx",
                "models/bert_tokenizer.json",
                outputPath
            )
            
            saveIndexBuiltFlag()
        }
    }
    
    private fun isIndexBuilt(): Boolean {
        return File(filesDir, "document_embeddings.json").exists()
    }
    
    private fun saveIndexBuiltFlag() {
        getSharedPreferences("rag", MODE_PRIVATE)
            .edit()
            .putBoolean("index_built", true)
            .apply()
    }
}

class MainActivity : AppCompatActivity() {
    
    private lateinit var sdk: AdrenoMenuSDK
    private val ragManager = RAGContextManager.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize SDK
        sdk = AdrenoMenuSDK.initialize(
            context = applicationContext,
            modelPath = "llm/Qwen3-1.7B-Q4_0.gguf",
            systemPrompt = """
                You are a knowledgeable technical support assistant.
                Use the provided context to answer questions accurately.
                If the answer is not in the context, say so.
            """.trimIndent(),
            gpuLayers = 27
        )
        
        // Enable RAG
        SystemPromptManagerEnhanced.getInstance().enableRAG()
        
        // Setup UI
        setupChat()
    }
    
    private fun setupChat() {
        val inputField = findViewById<EditText>(R.id.inputField)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val chatView = findViewById<TextView>(R.id.chatView)
        
        sendButton.setOnClickListener {
            val query = inputField.text.toString()
            if (query.isNotBlank()) {
                askQuestion(query, chatView)
                inputField.text.clear()
            }
        }
    }
    
    private fun askQuestion(query: String, chatView: TextView) {
        // Add user message
        runOnUiThread {
            chatView.append("\nYou: $query\n\nAssistant: ")
        }
        
        Thread {
            // RAG search
            val results = ragManager.search(query, 5)
            
            if (results != null && results.isNotEmpty()) {
                // Log what was found
                runOnUiThread {
                    Log.i("RAG", "Found ${results.size} relevant documents:")
                    results.forEach {
                        Log.i("RAG", "  - ${it.question} (score: ${it.score})")
                    }
                }
                
                // Inject context
                SystemPromptManagerEnhanced.getInstance().setRAGContext(results)
                sdk.refreshCache()
            } else {
                Log.w("RAG", "No relevant documents found")
            }
            
            // Generate response
            sdk.generateResponse(query) { token ->
                runOnUiThread {
                    chatView.append(token)
                }
            }
        }.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ragManager.close()
    }
}
```

---

## 🔧 Advanced Usage

### Custom Context Formatting

```kotlin
// Custom template for RAG context
val customTemplate = """
REFERENCE DOCUMENTS:
{DOCUMENTS}

Instructions: Use only the information from the reference documents above.
If the answer is not found, respond with "I don't have that information."
""".trimIndent()

val results = ragManager.search(query, 5)
val context = ragManager.getFormattedContext(results, customTemplate)
SystemPromptManagerEnhanced.getInstance().setRAGContextDirect(context)
```

### Filtering Results by Score

```kotlin
val results = ragManager.search(query, 10)
val highQualityResults = results?.filter { it.score > 0.7f }

if (highQualityResults != null && highQualityResults.isNotEmpty()) {
    SystemPromptManagerEnhanced.getInstance().setRAGContext(highQualityResults)
}
```

### Dynamic Index Updates

```kotlin
// Rebuild index when new documents are added
fun updateDocuments() {
    Thread {
        // Copy new Input_QA.json to assets
        
        // Rebuild index
        val ragManager = RAGContextManager.getInstance()
        val outputPath = File(filesDir, "document_embeddings.json").absolutePath
        
        ragManager.buildIndex(
            "docs/Input_QA.json",
            "models/gte-tiny.onnx",
            "models/bert_tokenizer.json",
            outputPath
        )
        
        // Reinitialize
        ragManager.close()
        ragManager.initialize(
            applicationContext,
            "models/gte-tiny.onnx",
            "models/bert_tokenizer.json",
            outputPath
        )
        
        Log.i("RAG", "✅ Index updated")
    }.start()
}
```

---

## 📊 Performance Tips

1. **Build Index Once**: Do it on first launch or app update
2. **Cache the Index**: Keep `document_embeddings.json` in internal storage
3. **Limit topK**: Use 3-5 documents for best results
4. **Background Processing**: Run RAG search in background thread
5. **Memory Management**: Close RAG manager when not needed

---

## 🐛 Troubleshooting

### Issue: "Index not found"
**Solution**: Build the index first using `buildIndex()`

### Issue: "No relevant documents found"
**Solution**: Check if your `Input_QA.json` contains relevant Q&A pairs

### Issue: "Out of memory"
**Solution**: Reduce the number of documents in Input_QA.json or use smaller batches

### Issue: "Slow search performance"
**Solution**: The first search is slower (loading model). Subsequent searches are fast (~10-30ms)

---

## 📝 Best Practices

1. **Document Quality**: Ensure Q&A pairs in Input_QA.json are clear and accurate
2. **Context Length**: Keep RAG context under 2000 tokens
3. **Prompt Engineering**: Instruct the LLM to use the context appropriately
4. **Error Handling**: Always check if RAG returns results before injecting context
5. **User Feedback**: Show users when RAG context is being used

---

## 🎓 Example Prompts

### Technical Documentation
```
"You are a technical support assistant. Use the reference manual provided below to answer questions. Be precise and cite specific sections when possible."
```

### FAQ System
```
"You are a customer service assistant. Answer questions using the FAQ database provided. If the question is not covered, politely say you don't have that information."
```

### Product Information
```
"You are a product specialist. Use the product specifications and features listed below to answer customer questions accurately."
```

---

**Your RAG system is now ready! 🚀**
