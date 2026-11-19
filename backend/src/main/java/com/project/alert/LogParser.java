package com.project.alert;

import com.project.dsa.KMPMatcher;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formal EBNF Log Parser.
 * Grammar:
 * <line> ::= <ts> " | " <level> " | SKU=" <sku> " | EVENT=" <type> " | MSG=" <text>
 *
 * Uses compiled regex ONLY for structured field extraction;
 * uses KMP for log signature detection inside MSG.
 */
public class LogParser {

    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z?)\\s*\\|\\s*(INFO|WARN|ERROR)\\s*\\|\\s*SKU=([^|\\s]+)\\s*\\|\\s*EVENT=([^|\\s]+)\\s*\\|\\s*MSG=(.*)$"
    );

    @Getter
    @Builder
    @ToString
    public static class ParsedLogEvent {
        private final Instant timestamp;
        private final String level;
        private final String sku;
        private final String eventType;
        private final String message;
        private final Set<String> detectedPatterns;
        private final String rawLine;
    }

    private final KMPMatcher kmpMatcher;

    public LogParser(KMPMatcher kmpMatcher) {
        this.kmpMatcher = kmpMatcher;
    }

    /**
     * Parses a raw log string line into structured fields.
     * Returns Optional.empty() if line is malformed.
     */
    public Optional<ParsedLogEvent> parse(String logLine) {
        if (logLine == null || logLine.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = LOG_LINE_PATTERN.matcher(logLine.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            Instant ts;
            String tsString = matcher.group(1);
            if (!tsString.endsWith("Z")) {
                tsString += "Z";
            }
            ts = Instant.parse(tsString);

            String level = matcher.group(2);
            String sku = matcher.group(3).trim();
            String eventType = matcher.group(4).trim();
            String msg = matcher.group(5).trim();

            // Run pure KMP signature detection on the extracted message text
            Set<String> patterns = kmpMatcher.scanText(msg);

            return Optional.of(ParsedLogEvent.builder()
                    .timestamp(ts)
                    .level(level)
                    .sku(sku)
                    .eventType(eventType)
                    .message(msg)
                    .detectedPatterns(patterns)
                    .rawLine(logLine)
                    .build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
