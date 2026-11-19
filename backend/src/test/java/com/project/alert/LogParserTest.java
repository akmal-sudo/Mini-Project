package com.project.alert;

import com.project.dsa.KMPMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ALERT")
class LogParserTest {

    private LogParser logParser;
    private KMPMatcher kmpMatcher;

    @BeforeEach
    void setUp() {
        kmpMatcher = new KMPMatcher();
        kmpMatcher.registerPattern("STOCK_OUT", "out of stock");
        kmpMatcher.registerPattern("LOW_STOCK", "low stock threshold reached");
        kmpMatcher.registerPattern("ORDER_FAILED", "insufficient stock for order");
        kmpMatcher.registerPattern("RESTOCK_FAILED", "supplier delivery delayed");

        logParser = new LogParser(kmpMatcher);
    }

    @Test
    @DisplayName("Parse valid log line conforming to EBNF grammar")
    void testParseValidLine() {
        String line = "2026-10-18T10:15:30.123Z | WARN | SKU=SKU-MILK-001 | EVENT=ORDER | MSG=Item low stock threshold reached for order ORD-101";
        Optional<LogParser.ParsedLogEvent> result = logParser.parse(line);

        assertTrue(result.isPresent());
        LogParser.ParsedLogEvent event = result.get();
        assertEquals(Instant.parse("2026-10-18T10:15:30.123Z"), event.getTimestamp());
        assertEquals("WARN", event.getLevel());
        assertEquals("SKU-MILK-001", event.getSku());
        assertEquals("ORDER", event.getEventType());
        assertEquals("Item low stock threshold reached for order ORD-101", event.getMessage());
        assertEquals(line, event.getRawLine());
        assertNotNull(event.toString());
        assertTrue(event.getDetectedPatterns().contains("LOW_STOCK"));
    }

    @Test
    @DisplayName("Parse line with timestamp missing trailing Z")
    void testParseLineTimestampWithoutZ() {
        String line = "2026-10-18T10:15:30 | ERROR | SKU=SKU-BREAD-002 | EVENT=ORDER | MSG=insufficient stock for order ORD-102";
        Optional<LogParser.ParsedLogEvent> result = logParser.parse(line);

        assertTrue(result.isPresent());
        assertEquals("ERROR", result.get().getLevel());
        assertEquals("SKU-BREAD-002", result.get().getSku());
        assertTrue(result.get().getDetectedPatterns().contains("ORDER_FAILED"));
    }

    @Test
    @DisplayName("Reject malformed log lines")
    void testParseMalformedLines() {
        assertFalse(logParser.parse(null).isPresent());
        assertFalse(logParser.parse("").isPresent());
        assertFalse(logParser.parse("   ").isPresent());
        assertFalse(logParser.parse("random unformatted log text").isPresent());
        assertFalse(logParser.parse("2026-10-18T10:15:30Z | BADLEVEL | SKU=SKU-1 | EVENT=ORDER | MSG=test").isPresent());
        assertFalse(logParser.parse("invalid-date | INFO | SKU=SKU-1 | EVENT=ORDER | MSG=test").isPresent());
        assertFalse(logParser.parse("2026-10-18T10:15:30Z | INFO | WRONGSKU | EVENT=ORDER | MSG=test").isPresent());
    }

    @Test
    @DisplayName("Parse line detecting multiple KMP signatures in MSG")
    void testParseMultipleSignatures() {
        String line = "2026-10-18T10:15:30Z | ERROR | SKU=SKU-EGGS-003 | EVENT=RESTOCK | MSG=supplier delivery delayed and item out of stock completely";
        Optional<LogParser.ParsedLogEvent> result = logParser.parse(line);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getDetectedPatterns().size());
        assertTrue(result.get().getDetectedPatterns().contains("RESTOCK_FAILED"));
        assertTrue(result.get().getDetectedPatterns().contains("STOCK_OUT"));
    }
}
