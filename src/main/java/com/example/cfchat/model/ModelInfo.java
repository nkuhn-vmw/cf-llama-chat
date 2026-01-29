package com.example.cfchat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelInfo {
    private String id;
    private String name;
    private String provider;
    private String description;
    private boolean available;
}
