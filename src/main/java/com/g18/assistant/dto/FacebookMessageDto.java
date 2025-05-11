package com.g18.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacebookMessageDto {
    
    @JsonProperty("object")
    private String object;
    
    @JsonProperty("entry")
    private List<Entry> entries;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("time")
        private Long time;
        
        @JsonProperty("messaging")
        private List<Messaging> messaging;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Messaging {
        @JsonProperty("sender")
        private Participant sender;
        
        @JsonProperty("recipient")
        private Participant recipient;
        
        @JsonProperty("timestamp")
        private Long timestamp;
        
        @JsonProperty("message")
        private Message message;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        @JsonProperty("id")
        private String id;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        @JsonProperty("mid")
        private String mid;
        
        @JsonProperty("text")
        private String text;
    }
} 