package com.edu.wzu.app;

public class PredictionResponse {
    private String videoId;
    private String predicted_class;
    private String average_confidence;
    private String score;
    private  String chinese_id;
    private  String ai_suggestion;
    public String getVideoId() {
        return videoId;
    }

    public void setPrediction(String predicted_class) {
        this.predicted_class = predicted_class;
    }
    public String getPrediction() {
        return predicted_class;
    }

    public String getConfidence() {
        return average_confidence;
    }

    public String getScore() {
        return score;
    }
    public  String getChinese_id(){
        return  chinese_id;
    }
    public String getAi_suggestion(){
        return  ai_suggestion;
    }
}

