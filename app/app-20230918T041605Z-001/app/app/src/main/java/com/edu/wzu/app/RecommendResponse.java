package com.edu.wzu.app;
import java.util.List;
public class RecommendResponse {
    private List<Recommendation> recommendations;

    public List<Recommendation> getRecommendations() {
        return recommendations;
    }

    public static class Recommendation {
        private String title;
        private String url;

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }
    }
}
