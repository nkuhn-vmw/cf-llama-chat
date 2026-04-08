// src/main/java/com/example/cfchat/service/wiki/WikiContextLoader.java
package com.example.cfchat.service.wiki;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class WikiContextLoader {
    // Full implementation in Task 11. Stub methods used by WikiService now.
    public void invalidate(UUID userId) { /* no-op until Task 11 */ }
    public String loadIndexBlock(UUID userId) { return ""; }
}
