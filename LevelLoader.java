/**
 Deserialises pre-designed level layout from JSON file and returns complete list of
 GameElement instances in their initial positions. Bridges level-design data
 (JSON in resources/levels/) and runtime entity model. Instantiates Platform, Portal,
 DarkCrawler, LoreFragment, Trigger with initial positions/properties. Includes
 lightweight inner JSON parser for the level file format without external libraries.
 */

import java.io.BufferedReader;        // Line-by-line file reading wrapped around the InputStream for efficiency
import java.io.IOException;           // Checked exception from BufferedReader.readLine() and InputStream operations
import java.io.InputStream;           // Raw byte stream from the classpath or filesystem for the level JSON file
import java.io.InputStreamReader;     // Adapts the InputStream to a character Reader so BufferedReader can wrap it
import java.nio.charset.StandardCharsets; // UTF-8 charset constant for consistent text decoding on all platforms
import java.util.ArrayList;           // ArrayList implementation for the element list and parsed JSON arrays
import java.util.HashMap;             // HashMap used for the parsed JSON object maps and the trigger params map
import java.util.List;                // List interface for the returned element collection and intermediate parsed arrays
import java.util.Map;                 // Map interface for the parsed JSON object maps

public class LevelLoader { // Stateful instance (no static fields); one instance is sufficient for the whole session

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public LevelLoader() {
        // Construct level loader; all I/O deferred to loadLevel(); no fields to initialize
    }

    // =========================================================================
    // Inner class — LoadResult
    // =========================================================================

    public static class LoadResult { // Bundle elements list and configured LevelState for loaded level

        public final List<GameElement> elements; // Platforms, portal, crawlers, fragments, triggers

        public final LevelState state; // Pre-configured LevelState with time limit, block budget, act phase

        public LoadResult(List<GameElement> elements, LevelState state) { // Bundle elements and state
            this.elements = elements;
            this.state    = state;
        }
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    public LoadResult loadLevel(int levelNum) { // Load level definition from resources/levels/level_0N.json; returns LoadResult with elements and state
        System.out.println("LevelLoader: attempting to load level " + levelNum); // Diagnostic: confirms which level is being loaded (visible in server console)
        List<GameElement> elements = new ArrayList<>();                            // Collect all instantiated entities here; returned in the LoadResult
        LevelState state = new LevelState();                                       // Default LevelState; populated from the JSON root object below

        String fileName = String.format("levels/level_%02d.json", levelNum);      // Format the filename with zero-padded level number: "levels/level_01.json"
        String jsonText = readResourceFile(fileName);                              // Read the file from classpath or filesystem; returns null on failure
        if (jsonText == null) {                                                    // File not found or read error: return an empty result
            return new LoadResult(elements, state);                                // Empty result; caller runs with no entities (safe degradation)
        }
        System.out.println("LevelLoader: file found, parsing...");                // Diagnostic: file was read successfully; parse is about to begin

        Map<String, Object> root; // Parsed JSON root object; all level data is accessed from this map
        try {
            root = JSONParser.parseObject(jsonText); // Delegate to the lightweight inner parser; throws on malformed JSON
        } catch (Exception e) {
            System.out.println("LevelLoader ERROR: " + e.getMessage()); // Log the parse error for debugging; visible in the console
            e.printStackTrace();                                          // Full stack trace for development diagnostics
            return new LoadResult(elements, state);                       // Return empty result; caller continues with no entities
        }

        // ---- Configure LevelState from the JSON root ----
        state.currentLevel    = getInt(root, "levelNum", levelNum);      // Level index from JSON; defaults to the parameter value
        int timeLimitSec      = getInt(root, "timeLimit", 300);          // Time limit in seconds; default 300 s (5 minutes)
        state.timeRemainingMs = timeLimitSec * 1000L;                    // Convert seconds to milliseconds for the countdown timer
        state.blockBudget     = getInt(root, "blockBudget", 20);         // Apprentice's starting block budget; default 20 blocks

        int act = getInt(root, "act", 1); // Act number (1, 2, or 3); drives the GamePhase assignment below
        switch (act) {
            case 1:  state.currentPhase = LevelState.GamePhase.ACT1; break; // Act 1: early levels; full light, no special mechanics
            case 2:  state.currentPhase = LevelState.GamePhase.ACT2; break; // Act 2: mid levels; DarkCrawlers active; faithful meter unlocks
            case 3:  state.currentPhase = LevelState.GamePhase.ACT3; break; // Act 3: late levels; INVISIBLE platforms and darkness mechanics
            default: state.currentPhase = LevelState.GamePhase.ACT1; break; // Fallback to ACT1 for any unrecognised act value
        }

        // ---- Platforms ----
        List<Map<String, Object>> platforms = getArray(root, "platforms"); // Extract the platforms array; returns empty list if key is absent
        for (Map<String, Object> p : platforms) {                          // Iterate each platform definition object
            String typeStr = getString(p, "type", "BRICK");                // Platform type string; defaults to BRICK if missing
            Platform.PlatformType pType = parsePlatformType(typeStr);      // Convert the type string to the PlatformType enum constant
            int px = getInt(p, "x", 0);                                    // Platform left-edge x coordinate; default 0
            int py = getInt(p, "y", 0);                                    // Platform top-edge y coordinate; default 0

            if (p.containsKey("w") || p.containsKey("h")) {               // Explicit dimensions supplied in the JSON: use the custom-size constructor
                int pw = getInt(p, "w", 64);                               // Custom width; default 64 px if "w" key is present but value missing
                int ph = getInt(p, "h", 16);                               // Custom height; default 16 px if "h" key is present but value missing
                elements.add(new Platform(pType, px, py, pw, ph));         // Instantiate the Platform with explicit dimensions
            } else {                                                        // No explicit dimensions: use the type-defaulting single-position constructor
                elements.add(new Platform(pType, px, py));                 // Instantiate with type-derived default width and height
            }
        }

        // ---- Portal ----
        Object portalObj = root.get("portal"); // Retrieve the portal entry; may be absent for levels that don't have one
        if (portalObj instanceof Map) {         // Only instantiate a Portal if the JSON entry is a map (object)
            @SuppressWarnings("unchecked")
            Map<String, Object> portalMap = (Map<String, Object>) portalObj; // Safe cast: confirmed instanceof Map above
            int portalX = getInt(portalMap, "x", 940); // Portal x coordinate; default 940 (near centre-right of the standard canvas)
            int portalY = getInt(portalMap, "y", 200); // Portal y coordinate; default 200 (upper portion of the canvas)
            elements.add(new Portal(portalX, portalY)); // Add the Portal entity at the specified position
        }

        // ---- DarkCrawlers REMOVED (P9.3') ----
        // The DarkCrawler class was deleted; the new hazard suite is placed
        // programmatically via LevelGenerator helpers, not via JSON. This
        // dormant loader is unused at runtime (LevelRegistry handles all level
        // construction now), but the original crawler-parsing block was deleted
        // so the file still compiles after DarkCrawler.java's removal.

        // ---- LoreFragments ----
        List<Map<String, Object>> fragments = getArray(root, "fragments"); // Extract the fragments array; returns empty list if key is absent
        for (Map<String, Object> f : fragments) {                           // Iterate each fragment definition object
            String id        = getString(f, "id", "UNKNOWN");              // Fragment unique ID; UNKNOWN is a QA-visible fallback
            int fx           = getInt(f, "x", 0);                          // Fragment x position; default 0
            int fy           = getInt(f, "y", 0);                          // Fragment y position; default 0
            String body      = getString(f, "bodyText", "");               // Narrative body text; empty string if not supplied
            String unlockStr = getString(f, "unlock", "NONE");             // AbilityUnlock name string; defaults to "NONE"
            LoreFragment.AbilityUnlock unlock = parseAbilityUnlock(unlockStr); // Convert unlock string to enum constant
            elements.add(new LoreFragment(id, body, unlock, fx, fy));         // Instantiate the LoreFragment at its world position
        }

        // ---- Special Triggers ----
        List<Map<String, Object>> triggers = getArray(root, "specialTriggers"); // Extract the specialTriggers array; returns empty list if absent
        for (Map<String, Object> t : triggers) {                                 // Iterate each trigger definition object
            String triggerType = getString(t, "type", "UNKNOWN"); // Trigger type string; matched by GameStarter to determine dispatch logic
            int tx = getInt(t, "x", 0);                           // Trigger left-edge x position; default 0
            int ty = getInt(t, "y", 0);                           // Trigger top-edge y position; default 0

            Map<String, Object> params = new HashMap<>();          // Initialise an empty params map; populated below if JSON has a "params" key
            Object rawParams = t.get("params");                    // Retrieve the "params" sub-object from the trigger definition
            if (rawParams instanceof Map) {                        // Only process if the entry is actually a map
                @SuppressWarnings("unchecked")
                Map<String, Object> castParams = (Map<String, Object>) rawParams; // Safe cast: confirmed instanceof Map above
                params.putAll(castParams);                         // Copy all params key-value pairs into the local map
            }

            elements.add(new Trigger(triggerType, tx, ty, params)); // Instantiate the Trigger with its type, position, and params
        }

        System.out.println("LevelLoader: loaded " + elements.size() + " entities"); // Diagnostic: confirms how many entities were instantiated
        return new LoadResult(elements, state); // Bundle and return the entity list and configured LevelState
    }

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    private String readResourceFile(String path) { // Read resource file from classpath or filesystem; return UTF-8 string or null on failure
        // Try classpath first: works in JAR deployments and when resources are on the classpath
        InputStream is = getClass().getClassLoader().getResourceAsStream(path); // Classpath lookup: null if not found
        if (is == null) {                                                        // Classpath lookup failed: try filesystem fallback
            java.io.File file = new java.io.File("resources/" + path);          // First filesystem path: "resources/levels/level_01.json"
            if (!file.exists()) {
                file = new java.io.File("resources", path);                     // Alternative filesystem path with File(dir, name) constructor
            }
            if (!file.exists()) {                                               // File not found on the filesystem either
                System.err.println("LevelLoader: resource not found: " + path); // Log for diagnostic; will trigger empty-result path in loadLevel()
                return null;                                                     // Return null: caller will return an empty LoadResult
            }
            try {
                is = new java.io.FileInputStream(file);                         // Open the file from the filesystem
            } catch (java.io.FileNotFoundException e) {
                System.err.println("LevelLoader: cannot open file: " + file.getPath()); // Log the failure with the resolved path for debugging
                return null;                                                              // Return null: caller will return an empty LoadResult
            }
        }

        // Read all lines into a StringBuilder and return the full text
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) { // UTF-8 encoding ensures special characters in lore text survive
            StringBuilder sb = new StringBuilder();                    // Accumulate all lines of the file
            String line;
            while ((line = reader.readLine()) != null) {               // readLine() returns null at end of stream
                sb.append(line).append('\n');                           // Append each line with a newline; preserves the original line breaks
            }
            return sb.toString();                                       // Return the complete file contents as a single string
        } catch (IOException e) {
            System.err.println("LevelLoader: I/O error reading " + path + ": " + e.getMessage()); // Log the I/O error with the path for diagnosis
            return null; // Return null: loadLevel() will catch this and return an empty LoadResult
        }
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static Platform.PlatformType parsePlatformType(String s) { // Convert platform type string (case-insensitive) to PlatformType enum; default to BRICK
        try {
            return Platform.PlatformType.valueOf(s.trim().toUpperCase()); // Trim whitespace and convert to upper-case for case-insensitive matching
        } catch (IllegalArgumentException e) {
            return Platform.PlatformType.BRICK; // Unknown type string: safe fallback to the most common platform type
        }
    }

    private static LoreFragment.AbilityUnlock parseAbilityUnlock(String s) { // Convert ability unlock string (case-insensitive) to AbilityUnlock enum; default to NONE
        try {
            return LoreFragment.AbilityUnlock.valueOf(s.trim().toUpperCase()); // Normalise and look up the enum constant
        } catch (IllegalArgumentException e) {
            return LoreFragment.AbilityUnlock.NONE; // Unknown unlock string: treat as narrative-only fragment
        }
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) { // Get integer from JSON map; return default if missing or non-numeric
        Object val = map.get(key);            // Look up the value by key; null if absent
        if (val instanceof Number) {          // JSON numbers are parsed as Integer, Long, or Double depending on format
            return ((Number) val).intValue(); // Convert to int; truncates fractional part for floating-point JSON values
        }
        return defaultVal; // Key missing or value is not a number: return the caller-supplied default
    }

    private static String getString(Map<String, Object> map, String key, String defaultVal) { // Get string from JSON map; return default if missing or non-string
        Object val = map.get(key);    // Look up the value by key; null if absent
        if (val instanceof String) {  // Only return the value if it is actually a String
            return (String) val;      // Cast is safe: instanceof check guarantees the type
        }
        return defaultVal; // Key missing or value is not a string: return the caller-supplied default
    }

    @SuppressWarnings("unchecked") // Cast is safe; JSONParser only stores Map<String,Object> in arrays
    private static List<Map<String, Object>> getArray(Map<String, Object> map, String key) { // Get array from JSON map; return empty list if missing or non-array
        Object val = map.get(key);  // Look up the value by key; null if absent
        if (val instanceof List) {  // Only process if the value is actually a List (JSON array)
            return (List<Map<String, Object>>) val; // Cast: each element is a parsed JSON object (Map<String,Object>)
        }
        return new ArrayList<>(); // Key missing or value is not a list: return an empty list to avoid null-checks in callers
    }

    // =========================================================================
    // Static inner class — JSONParser
    // =========================================================================

    static class JSONParser { // Lightweight recursive-descent JSON parser for level data files; supports strings, numbers, booleans, null, objects, arrays

        private final String src; // Source text; immutable

        private int pos; // Current position in src; zero-based index

        private JSONParser(String src) { // Construct parser for given JSON source
            this.src = src;
            this.pos = 0;
        }

        // ----- Public entry points -----

        public static Map<String, Object> parseObject(String json) { // Parse JSON text as top-level object; return Map<String,Object>
            JSONParser parser = new JSONParser(json); // Create a fresh parser for this text; pos starts at 0
            parser.skipWhitespace();                  // Skip any leading whitespace before the opening brace
            Map<String, Object> result = parser.readObject(); // Parse the top-level object
            return result; // Return the fully-parsed map to the caller
        }

        // ----- Core readers -----

        private Object readValue() { // Read next JSON value; dispatch to appropriate reader based on leading character
            skipWhitespace(); // Consume whitespace before the next value
            if (pos >= src.length()) {
                throw error("Unexpected end of input"); // No characters remain: malformed JSON
            }
            char c = src.charAt(pos); // Peek at the next character to determine the value type
            if (c == '"') return readString();                                   // String value: starts with a quote
            if (c == '{') return readObject();                                   // Object value: starts with a brace
            if (c == '[') return readArray();                                    // Array value: starts with a bracket
            if (c == 't' || c == 'f') return readBoolean();                     // Boolean: starts with 't' (true) or 'f' (false)
            if (c == 'n') return readNull();                                     // Null value: starts with 'n'
            if (c == '-' || (c >= '0' && c <= '9')) return readNumber();        // Number: starts with a minus or a digit
            throw error("Unexpected character: " + c); // Nothing else is valid in JSON at value position
        }

        private Map<String, Object> readObject() { // Read JSON object and return as Map<String,Object>
            expect('{');                          // Consume the opening brace; throws if not present
            Map<String, Object> map = new HashMap<>(); // Accumulate key-value pairs here
            skipWhitespace();                     // Skip whitespace before the first key or closing brace
            if (pos < src.length() && src.charAt(pos) == '}') { // Empty object: "{}"
                pos++;    // Consume the closing brace
                return map; // Return an empty map
            }
            while (true) {                            // Read key-value pairs until the closing brace
                skipWhitespace();                     // Skip whitespace before each key
                String key = readString();            // Read the key (always a JSON string)
                skipWhitespace();                     // Skip whitespace between the key and the colon
                expect(':');                          // Consume the colon separator; throws if not present
                Object value = readValue();           // Recursively read the value (any JSON type)
                map.put(key, value);                  // Store the key-value pair in the map
                skipWhitespace();                     // Skip whitespace after the value
                if (pos >= src.length()) break;       // End of input: exit the loop (malformed but recoverable)
                char c = src.charAt(pos);
                if (c == '}') { pos++; break; }      // Closing brace: object is complete
                if (c == ',') { pos++; continue; }   // Comma: more pairs follow; continue the loop
                throw error("Expected ',' or '}' but got: " + c); // Unexpected character: malformed JSON
            }
            return map; // Return the fully populated map
        }

        private List<Object> readArray() { // Read JSON array and return as List<Object>
            expect('[');                       // Consume the opening bracket; throws if not present
            List<Object> list = new ArrayList<>(); // Accumulate elements here
            skipWhitespace();                  // Skip whitespace before the first element or closing bracket
            if (pos < src.length() && src.charAt(pos) == ']') { // Empty array: "[]"
                pos++;    // Consume the closing bracket
                return list; // Return an empty list
            }
            while (true) {                   // Read elements until the closing bracket
                Object value = readValue();  // Recursively read the next element (any JSON type)
                list.add(value);             // Add the element to the list
                skipWhitespace();            // Skip whitespace after the element
                if (pos >= src.length()) break;     // End of input: exit the loop
                char c = src.charAt(pos);
                if (c == ']') { pos++; break; }    // Closing bracket: array is complete
                if (c == ',') { pos++; continue; } // Comma: more elements follow
                throw error("Expected ',' or ']' but got: " + c); // Unexpected character
            }
            return list; // Return the fully populated list
        }

        private String readString() { // Read JSON string including escape sequences
            skipWhitespace();                  // Skip whitespace before the opening quote
            expect('"');                       // Consume the opening double-quote
            StringBuilder sb = new StringBuilder(); // Accumulate the string characters here
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') {                // Closing double-quote: string is complete
                    pos++;                     // Consume the closing quote
                    return sb.toString();      // Return the accumulated string
                }
                if (c == '\\') {              // Backslash: handle the escape sequence
                    pos++;                    // Consume the backslash
                    if (pos >= src.length()) throw error("Unexpected end of string escape"); // Truncated escape
                    char esc = src.charAt(pos); // The escape character following the backslash
                    switch (esc) {
                        case '"':  sb.append('"');  break; // Escaped double-quote
                        case '\\': sb.append('\\'); break; // Escaped backslash
                        case '/':  sb.append('/');  break; // Escaped forward-slash (valid in JSON)
                        case 'n':  sb.append('\n'); break; // Newline
                        case 'r':  sb.append('\r'); break; // Carriage return
                        case 't':  sb.append('\t'); break; // Tab
                        case 'b':  sb.append('\b'); break; // Backspace
                        case 'f':  sb.append('\f'); break; // Form feed
                        case 'u':                          // Unicode escape (backslash-u-hex-hex-hex-hex)
                            if (pos + 4 >= src.length()) throw error("Incomplete unicode escape"); // Need 4 hex digits
                            String hex = src.substring(pos + 1, pos + 5);   // Extract 4 hex characters
                            sb.append((char) Integer.parseInt(hex, 16));     // Parse hex to char and append
                            pos += 4;                                         // Advance past the 4 hex digits
                            break;
                        default:
                            sb.append(esc); // Unknown escape: include the escaped character as-is
                    }
                } else {
                    sb.append(c); // Normal character: append directly
                }
                pos++; // Advance past the current character (whether normal or escape-sequence)
            }
            throw error("Unterminated string"); // Reached end of source without a closing quote
        }

        private Number readNumber() { // Read JSON number (int/long/double based on format)
            int start = pos;                                                  // Record the start position for substring extraction later
            if (pos < src.length() && src.charAt(pos) == '-') pos++;         // Optional leading minus sign
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++; // Integer digits
            boolean isFloat = false;                                          // Tracks whether the number has fractional or exponent parts
            if (pos < src.length() && src.charAt(pos) == '.') {              // Decimal point: switch to floating-point mode
                isFloat = true;
                pos++;                                                        // Consume the decimal point
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++; // Fractional digits
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) { // Exponent part
                isFloat = true;                                               // Exponent means floating-point
                pos++;                                                        // Consume 'e' or 'E'
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++; // Optional sign
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++; // Exponent digits
            }
            String numStr = src.substring(start, pos); // Extract the number string from the source
            if (isFloat) {
                return Double.parseDouble(numStr); // Float/double: parse as 64-bit IEEE 754
            } else {
                long val = Long.parseLong(numStr);                         // Integer: parse as long first to handle large values
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;                                       // Fits in int: return as Integer for convenient getInt() access
                }
                return val; // Doesn't fit in int: return as Long
            }
        }

        private Boolean readBoolean() { // Read JSON boolean (true/false)
            if (src.startsWith("true", pos)) {    // Check for the literal "true"
                pos += 4;                          // Consume 4 characters ("true")
                return Boolean.TRUE;               // Return the Boolean constant
            }
            if (src.startsWith("false", pos)) {   // Check for the literal "false"
                pos += 5;                          // Consume 5 characters ("false")
                return Boolean.FALSE;              // Return the Boolean constant
            }
            throw error("Expected boolean"); // Neither "true" nor "false" found: malformed JSON
        }

        private Object readNull() { // Read JSON null literal
            if (src.startsWith("null", pos)) { // Check for the literal "null"
                pos += 4;                       // Consume 4 characters ("null")
                return null;                    // Return Java null for the JSON null literal
            }
            throw error("Expected null"); // "null" not found: malformed JSON
        }

        // ----- Utilities -----

        private void skipWhitespace() { // Skip whitespace characters (space, tab, newline, carriage return)
            while (pos < src.length()) {                              // Loop until end of source or a non-whitespace character
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') { // Standard JSON whitespace characters
                    pos++;                                             // Skip this whitespace character
                } else {
                    break; // Non-whitespace found: stop skipping
                }
            }
        }

        private void expect(char expected) { // Assert current character equals expected; advance position
            skipWhitespace(); // Allow whitespace before structural characters like '{', ':', ','
            if (pos >= src.length() || src.charAt(pos) != expected) { // Character is missing or doesn't match
                char actual = pos < src.length() ? src.charAt(pos) : '\0'; // '\0' if end of input
                throw error("Expected '" + expected + "' but got '" + actual + "'"); // Descriptive error for the caller
            }
            pos++; // Consume the expected character; advance past it
        }

        private IllegalArgumentException error(String msg) { // Construct error with position and context snippet
            int context = Math.min(pos + 20, src.length());           // Show up to 20 characters beyond the error position
            String snippet = src.substring(pos, context).replace("\n", "\\n"); // Replace newlines for single-line display
            return new IllegalArgumentException(
                    "JSON parse error at position " + pos + ": " + msg + " near: \"" + snippet + "\""); // Include position and context
        }
    }
}
