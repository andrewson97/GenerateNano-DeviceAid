package com.iterate.adreno.sdk.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Search result from vector similarity search
 */
public class SearchResult {
    private final String id;
    private final String text;
    private String question;
    private String answer;
    private final float score;
    private String file;
    private List<String> imageRefs;
    private String category;

    public SearchResult(String id, String text, float score) {
        this.id = id;
        this.text = text;
        this.score = score;
        this.imageRefs = new ArrayList<>();
    }
    
    // Constructor for precomputed vectors with question/answer
    public SearchResult(String id, String question, String answer, float score, String category) {
        this.id = id;
        this.text = question;
        this.question = question;
        this.answer = answer;
        this.score = score;
        this.category = category;
        this.imageRefs = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getQuestion() {
        return question != null ? question : text;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public float getScore() {
        return score;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public List<String> getImageRefs() {
        return imageRefs;
    }

    public void setImageRefs(List<String> imageRefs) {
        this.imageRefs = imageRefs;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String toString() {
        return String.format("SearchResult{id='%s', score=%.4f, question='%s'}", id, score, getQuestion());
    }
}
