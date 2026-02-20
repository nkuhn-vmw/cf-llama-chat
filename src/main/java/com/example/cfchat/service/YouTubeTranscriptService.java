package com.example.cfchat.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class YouTubeTranscriptService {

    private static final Pattern YT = Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]{11})");

    public boolean isYouTubeUrl(String url) {
        return url != null && YT.matcher(url).find();
    }

    public String getTranscript(String url) {
        Matcher m = YT.matcher(url);
        if (!m.find()) return "";
        String videoId = m.group(1);
        try {
            // Fetch YouTube page, extract captionTracks from ytInitialPlayerResponse
            Document page = Jsoup.connect("https://www.youtube.com/watch?v=" + videoId)
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();
            String captionUrl = extractCaptionUrl(page.html());
            if (captionUrl != null) {
                Document caps = Jsoup.connect(captionUrl)
                        .timeout(10000)
                        .get();
                return caps.select("text").stream()
                        .map(Element::text)
                        .collect(Collectors.joining(" "));
            }
        } catch (Exception e) {
            log.warn("Failed to get YouTube transcript for {}: {}", url, e.getMessage());
        }
        return "";
    }

    private String extractCaptionUrl(String html) {
        // Parse ytInitialPlayerResponse to find captionTracks
        // Look for "captionTracks" in the page source
        // Extract the baseUrl for English captions (or first available)
        try {
            int idx = html.indexOf("\"captionTracks\":");
            if (idx < 0) return null;
            String sub = html.substring(idx);
            int urlStart = sub.indexOf("\"baseUrl\":\"") + 11;
            int urlEnd = sub.indexOf("\"", urlStart);
            String url = sub.substring(urlStart, urlEnd).replace("\\u0026", "&");
            return url;
        } catch (Exception e) {
            log.debug("Could not extract caption URL: {}", e.getMessage());
            return null;
        }
    }
}
