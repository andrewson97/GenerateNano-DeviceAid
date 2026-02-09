# RAG Implementation for Adreno Q&A SDK

## Overview
This document describes the RAG (Retrieval-Augmented Generation) implementation added to the Adreno SDK, enabling semantic document search and context injection.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Query                              │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    RAGContextManager                            │
│  • search(query, topK)                                          │
│  • buildIndexFromJson(inputPath)                                │
│  • isIndexReady()                                               │
└────────────┬────────────────────────────┬───────────────────────┘
             │                            │
             ▼                            ▼
┌──────────────────────────┐   ┌─────────────────────────────────┐
│   VectorSearchEngine     │   │   DocumentIndexBuilder          │
│  • initialize()          │   │  • buildIndex(inputJson)        │
│  • query(text, topK)     │   │  • generateEmbeddings()         │
│  • cosineSimilarity()    │   │  • saveIndex()                  │
└────────────┬─────────────┘   └────────────┬────────────────────┘
             │                              │
             ▼                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              VectorEmbeddingGenerator (ONNX)                    │
│  • initialize()                                                 │
│  • generateEmbedding(text) → float[384]                         │
│  • tokenize(text)                                               │
└─────────────────────────────────────────────────────────────────┘
```

## Components

### 1. VectorEmbeddingGenerator
**Purpose**: Generate 384-dimensional embeddings using ONNX model

**Dependencies**:
- ONNX Runtime Java (`ai.onnxruntime:onnxruntime-android`)
- GTE-Tiny ONNX model (~15MB)
- BERT tokenizer JSON

**Key Methods**:
```java
boolean initialize()
float[] generateEmbedding(String text)
void close()
```

### 2. DocumentIndexBuilder
**Purpose**: Build vector index from Q&A JSON

**Input Format** (`Input_QA.json`):
```json
[
  {
    "page": 0,
    "qa_pairs": [
      {
        "question": "What is X?",
        "answer": "X is...",
        "image_refs": ["path/to/image.png"]
      }
    ]
  }
]
```

**Output Format** (`document_embeddings.json`):
```json
{
  "metadata": {
    "version": 1,
    "embeddingDim": 384,
    "docCount": 100
  },
  "embeddings": [
    {
      "id": "page0_q0",
      "text": "What is X?",
      "question": "What is X?",
      "answer": "X is...",
      "embedding": [0.123, -0.456, ...]
    }
  ]
}
```

### 3. VectorSearchEngine
**Purpose**: Search indexed documents using cosine similarity

**Search Algorithm**:
1. Generate query embedding
2. Compute cosine similarity with all documents
3. Apply hybrid boosting:
   - Exact match: +0.30
   - Keyword overlap: +0.05 per keyword
   - Fuzzy match: +0.025
4. Return top-K results

### 4. RAGContextManager
**Purpose**: Main API for RAG operations

**Key Methods**:
```java
// Initialize with model and index paths
void initialize(Context context, String modelPath, String tokenizerPath, String indexPath)

// Build index from Q&A JSON
boolean buildIndex(String inputJsonPath)

// Search documents
List<SearchResult> search(String query, int topK)

// Get formatted context for LLM
String getFormattedContext(List<SearchResult> results)

// Check if ready
boolean isIndexReady()
```

## Integration with Adreno SDK

### Option 1: Automatic RAG (Recommended)
```kotlin
// Initialize SDK with RAG
val sdk = AdrenoMenuSDK.initialize(
    context = context,
    modelPath = "models/Qwen3-1.7B-Q4_0.gguf",
    systemPrompt = "You are a helpful assistant.",
    gpuLayers = 27,
    ragConfig = RAGConfig(
        enabled = true,
        modelPath = "models/gte-tiny.onnx",
        tokenizerPath = "models/bert_tokenizer.json",
        indexPath = "docs/document_embeddings.json",
        topK = 5
    )
)

// Query automatically retrieves context
sdk.generateResponse("What is the reset procedure?") { token ->
    // RAG context is automatically injected
}
```

### Option 2: Manual RAG Control
```kotlin
// Initialize RAG separately
val ragManager = RAGContextManager.getInstance()
ragManager.initialize(context, modelPath, tokenizerPath, indexPath)

// Build index from Q&A JSON
ragManager.buildIndex("assets/Input_QA.json")

// Manual search and context building
val results = ragManager.search("reset procedure", 5)
val context = ragManager.getFormattedContext(results)

// Build system prompt with RAG context
val systemPrompt = """
You are a helpful assistant.

CONTEXT:
$context

Answer based on the provided context.
""".trimIndent()

// Initialize SDK with RAG context
sdk.initialize(context, modelPath, systemPrompt, gpuLayers)
```

## File Structure

```
sdk/src/main/java/com/iterate/adreno/sdk/
├── rag/
│   ├── VectorEmbeddingGenerator.java
│   ├── DocumentIndexBuilder.java
│   ├── VectorSearchEngine.java
│   ├── RAGContextManager.java
│   └── models/
│       ├── SearchResult.java
│       ├── RAGConfig.java
│       └── EmbeddingIndex.java
├── AdrenoMenuSDK.kt (updated)
└── SystemPromptManagerEnhanced.java (updated)
```

## Dependencies to Add

### build.gradle
```gradle
dependencies {
    // Existing dependencies...
    
    // ONNX Runtime for embeddings
    implementation 'ai.onnxruntime:onnxruntime-android:1.16.0'
    
    // JSON parsing (already included)
    implementation 'org.json:json:20230227'
}
```

### SDK Assets (Bundled in SDK)
```
sdk/src/main/assets/
└── models/
    ├── InterplayGTE.onnx      (~22MB) ✅ Already included
    └── bert_tokenizer.json    (~695KB) ✅ Already included
```

### Application Assets (Your app provides)
```
app/src/main/assets/
└── docs/
    └── Input_QA.json          (Your Q&A documents)
```

**Note:** The SDK bundles the generic ONNX model and tokenizer. Your application provides the document-specific Input_QA.json.

## Usage Examples

### Example 1: Technical Documentation Q&A
```kotlin
// Build index from manual PDF extracts
ragManager.buildIndex("assets/docs/user_manual_qa.json")

// Query
sdk.generateResponse("How do I reset the device?") { token ->
    // Answer based on manual content
}
```

### Example 2: FAQ System
```kotlin
// Build index from FAQ JSON
ragManager.buildIndex("assets/docs/faq.json")

// Query
sdk.generateResponse("What is the warranty period?") { token ->
    // Answer from FAQ
}
```

### Example 3: Product Specifications
```kotlin
// Build index from specs
ragManager.buildIndex("assets/docs/specifications_qa.json")

// Query with automatic context
sdk.generateResponse("What is the maximum speed?") { token ->
    // Answer from specifications
}
```

## Performance Characteristics

| Metric | Value |
|--------|-------|
| **Embedding Generation** | ~50-100ms per query |
| **Index Build Time** | ~10-20s for 1000 documents |
| **Search Time** | ~10-30ms for 1000 docs |
| **Model Size** | ~15MB (ONNX quantized) |
| **Memory Usage** | ~50MB (model + index) |
| **Index Size** | ~2MB per 1000 docs |

## Limitations & Considerations

1. **ONNX Model Required**: Adds ~15MB to APK size
2. **Index Build Time**: Initial indexing takes time (do at startup or background)
3. **Memory Usage**: Keep index in memory for fast access
4. **Token Limit**: Combined context + query must fit in model's context window
5. **Language**: Optimized for English (can work with other languages with different models)

## Future Enhancements

- [ ] Support for multiple indices (multi-domain)
- [ ] Dynamic index updates (add/remove documents at runtime)
- [ ] Hybrid search (BM25 + semantic)
- [ ] Reranking for better results
- [ ] Support for larger embedding models
- [ ] GPU-accelerated embedding generation

## References

- ONNX Runtime: https://onnxruntime.ai/
- GTE Models: https://huggingface.co/Alibaba-NLP/gte-small
- Cosine Similarity: https://en.wikipedia.org/wiki/Cosine_similarity
