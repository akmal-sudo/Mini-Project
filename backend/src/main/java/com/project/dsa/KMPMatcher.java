package com.project.dsa;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom Knuth-Morris-Pratt (KMP) Substring Matcher.
 * Precomputes the Longest Proper Prefix which is also Suffix (LPS) array for O(N) search.
 * Strictly avoids String.contains, String.indexOf, or regular expressions.
 */
public class KMPMatcher {

    private static class CompiledPattern {
        final String name;
        final char[] patternChars;
        final int[] lps;

        CompiledPattern(String name, String patternText) {
            this.name = name;
            this.patternChars = patternText.toCharArray();
            this.lps = buildLPS(this.patternChars);
        }
    }

    private final Map<String, CompiledPattern> patternRegistry = new ConcurrentHashMap<>();
    private final AtomicLong scansCount = new AtomicLong(0);
    private final AtomicLong matchesCount = new AtomicLong(0);
    private final AtomicLong comparisonsCount = new AtomicLong(0);

    public KMPMatcher() {
        // Pre-register default authoritative quick-commerce log signatures
        registerPattern("STOCK_OUT", "STOCK_OUT");
        registerPattern("LOW_STOCK", "LOW_STOCK");
        registerPattern("ORDER_FAILED", "ORDER_FAILED");
        registerPattern("RESTOCK_FAILED", "RESTOCK_FAILED");
    }

    /**
     * Precomputes the LPS array for a given pattern.
     * Complexity: O(M) where M is pattern length.
     */
    public static int[] buildLPS(char[] pattern) {
        int m = pattern.length;
        int[] lps = new int[m];
        if (m == 0) return lps;

        int len = 0;
        lps[0] = 0;
        int i = 1;

        while (i < m) {
            if (pattern[i] == pattern[len]) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
        return lps;
    }

    /**
     * Registers or dynamically updates a log pattern signature.
     */
    public void registerPattern(String name, String patternText) {
        if (name == null || patternText == null || patternText.isEmpty()) {
            return;
        }
        patternRegistry.put(name, new CompiledPattern(name, patternText));
    }

    /**
     * Scans text against all registered patterns using pure KMP.
     * Returns the set of pattern names detected in the text.
     */
    public Set<String> scanText(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptySet();
        }

        scansCount.incrementAndGet();
        char[] txt = text.toCharArray();
        Set<String> detected = new HashSet<>();

        for (CompiledPattern cp : patternRegistry.values()) {
            if (kmpSearch(txt, cp.patternChars, cp.lps) != -1) {
                detected.add(cp.name);
                matchesCount.incrementAndGet();
            }
        }
        return detected;
    }

    /**
     * Tests if text contains a specific registered pattern using KMP.
     */
    public boolean matches(String text, String patternName) {
        if (text == null || text.isEmpty() || patternName == null) {
            return false;
        }

        CompiledPattern cp = patternRegistry.get(patternName);
        if (cp == null) {
            return false;
        }

        scansCount.incrementAndGet();
        boolean found = kmpSearch(text.toCharArray(), cp.patternChars, cp.lps) != -1;
        if (found) {
            matchesCount.incrementAndGet();
        }
        return found;
    }

    /**
     * Executes KMP search on character arrays, recording character comparison telemetry.
     * Complexity: O(N) where N is text length.
     *
     * @return index of match, or -1 if no match found.
     */
    public int kmpSearch(char[] text, char[] pattern, int[] lps) {
        int n = text.length;
        int m = pattern.length;

        if (m == 0) return 0;
        if (n < m) return -1;

        int i = 0; // index for text
        int j = 0; // index for pattern

        while (i < n) {
            comparisonsCount.incrementAndGet();
            if (pattern[j] == text[i]) {
                i++;
                j++;
            }

            if (j == m) {
                return i - j; // Match found at (i - j)
            } else if (i < n) {
                comparisonsCount.incrementAndGet();
                if (pattern[j] != text[i]) {
                    if (j != 0) {
                        j = lps[j - 1];
                    } else {
                        i++;
                    }
                }
            }
        }
        return -1;
    }

    public long getScansCount() {
        return scansCount.get();
    }

    public long getMatchesCount() {
        return matchesCount.get();
    }

    public long getComparisonsCount() {
        return comparisonsCount.get();
    }

    public Map<String, String> getRegisteredPatterns() {
        Map<String, String> map = new HashMap<>();
        patternRegistry.forEach((k, v) -> map.put(k, new String(v.patternChars)));
        return map;
    }
}
