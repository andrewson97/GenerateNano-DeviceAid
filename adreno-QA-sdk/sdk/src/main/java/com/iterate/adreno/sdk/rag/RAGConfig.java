package com.iterate.adreno.sdk.rag;

/**
 * Configuration for RAG (Retrieval-Augmented Generation)
 */
public class RAGConfig {
    private final boolean enabled;
    private final String modelPath;
    private final String tokenizerPath;
    private final String indexPath;
    private final int topK;

    private RAGConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.modelPath = builder.modelPath;
        this.tokenizerPath = builder.tokenizerPath;
        this.indexPath = builder.indexPath;
        this.topK = builder.topK;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getTokenizerPath() {
        return tokenizerPath;
    }

    public String getIndexPath() {
        return indexPath;
    }

    public int getTopK() {
        return topK;
    }

    public static class Builder {
        private boolean enabled = false;
        private String modelPath;
        private String tokenizerPath;
        private String indexPath;
        private int topK = 5;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder modelPath(String modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder tokenizerPath(String tokenizerPath) {
            this.tokenizerPath = tokenizerPath;
            return this;
        }

        public Builder indexPath(String indexPath) {
            this.indexPath = indexPath;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public RAGConfig build() {
            return new RAGConfig(this);
        }
    }
}
