/**
 * Deserialises a pre-designed level layout from a JSON data file and returns the
 * complete list of {@link entities.GameElement} instances that make up that level's
 * initial state. Includes a lightweight inner JSON parser that handles the level file
 * format without requiring any external libraries.
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LevelLoader {

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code LevelLoader} ready to load level definitions. No I/O is
     * performed at construction time; all loading is deferred to
     * {@link #loadLevel(int)}.
     */
    public LevelLoader() {
        // No-arg constructor — deferred loading.
    }

    // =========================================================================
    // Inner class — LoadResult
    // =========================================================================

    /**
     * Bundles the list of instantiated game elements together with the configured
     * {@link LevelState} for a loaded level so that the caller receives both in a
     * single return value.
     */
    public static class LoadResult {

        /** All game elements (platforms, crawlers, fragments, triggers) for the level. */
        public final List<GameElement> elements;

        /** The level state configured with the level's time limit and block budget. */
        public final LevelState state;

        /**
         * Constructs a {@code LoadResult} with the given elements and state.
         *
         * @param elements the instantiated game elements
         * @param state    the configured level state
         */
        public LoadResult(List<GameElement> elements, LevelState state) {
            this.elements = elements;
            this.state = state;
        }
    }

    // -------------------------------------------------------------------------
    // Loading
    // -------------------------------------------------------------------------

    /**
     * Reads the layout definition for the specified level number from
     * {@code resources/levels/level_0N.json} and constructs the corresponding list of
     * {@link entities.GameElement} instances in their initial positions. The returned
     * {@link LoadResult} contains both the element list and a {@link LevelState}
     * configured with the level's time limit and block budget.
     *
     * @param levelNum the one-based index of the level to load (1-10)
     * @return a {@link LoadResult} containing all entities and the level state;
     *         returns an empty result if the level file cannot be found or parsed
     */
    public LoadResult loadLevel(int levelNum) {
        System.out.println("LevelLoader: attempting to load level " + levelNum);
        List<GameElement> elements = new ArrayList<>();
        LevelState state = new LevelState();

        String fileName = String.format("levels/level_%02d.json", levelNum);
        String jsonText = readResourceFile(fileName);
        if (jsonText == null) {
            return new LoadResult(elements, state);
        }
        System.out.println("LevelLoader: file found, parsing...");

        Map<String, Object> root;
        try {
            root = JSONParser.parseObject(jsonText);
        } catch (Exception e) {
            System.out.println("LevelLoader ERROR: " + e.getMessage());
            e.printStackTrace();
            return new LoadResult(elements, state);
        }

        // ----- Configure LevelState -----
        state.currentLevel = getInt(root, "levelNum", levelNum);
        int timeLimitSec = getInt(root, "timeLimit", 300);
        state.timeRemainingMs = timeLimitSec * 1000L;
        state.blockBudget = getInt(root, "blockBudget", 20);

        int act = getInt(root, "act", 1);
        switch (act) {
            case 1:  state.currentPhase = LevelState.GamePhase.ACT1; break;
            case 2:  state.currentPhase = LevelState.GamePhase.ACT2; break;
            case 3:  state.currentPhase = LevelState.GamePhase.ACT3; break;
            default: state.currentPhase = LevelState.GamePhase.ACT1; break;
        }

        // ----- Platforms -----
        List<Map<String, Object>> platforms = getArray(root, "platforms");
        for (Map<String, Object> p : platforms) {
            String typeStr = getString(p, "type", "BRICK");
            Platform.PlatformType pType = parsePlatformType(typeStr);
            int px = getInt(p, "x", 0);
            int py = getInt(p, "y", 0);

            if (p.containsKey("w") || p.containsKey("h")) {
                int pw = getInt(p, "w", 64);
                int ph = getInt(p, "h", 16);
                elements.add(new Platform(pType, px, py, pw, ph));
            } else {
                elements.add(new Platform(pType, px, py));
            }
        }

        // ----- Portal -----
        Object portalObj = root.get("portal");
        if (portalObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> portalMap = (Map<String, Object>) portalObj;
            int portalX = getInt(portalMap, "x", 940);
            int portalY = getInt(portalMap, "y", 200);
            elements.add(new Portal(portalX, portalY));
        }

        // ----- DarkCrawlers -----
        List<Map<String, Object>> crawlers = getArray(root, "crawlers");
        for (Map<String, Object> c : crawlers) {
            int cx = getInt(c, "x", 0);
            int cy = getInt(c, "y", 0);
            elements.add(new DarkCrawler(cx, cy));
        }

        // ----- LoreFragments -----
        List<Map<String, Object>> fragments = getArray(root, "fragments");
        for (Map<String, Object> f : fragments) {
            String id = getString(f, "id", "UNKNOWN");
            int fx = getInt(f, "x", 0);
            int fy = getInt(f, "y", 0);
            String body = getString(f, "bodyText", "");
            String unlockStr = getString(f, "unlock", "NONE");
            LoreFragment.AbilityUnlock unlock = parseAbilityUnlock(unlockStr);
            elements.add(new LoreFragment(id, body, unlock, fx, fy));
        }

        // ----- Special Triggers -----
        List<Map<String, Object>> triggers = getArray(root, "specialTriggers");
        for (Map<String, Object> t : triggers) {
            String triggerType = getString(t, "type", "UNKNOWN");
            int tx = getInt(t, "x", 0);
            int ty = getInt(t, "y", 0);

            Map<String, Object> params = new HashMap<>();
            Object rawParams = t.get("params");
            if (rawParams instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castParams = (Map<String, Object>) rawParams;
                params.putAll(castParams);
            }

            elements.add(new Trigger(triggerType, tx, ty, params));
        }

        System.out.println("LevelLoader: loaded " + elements.size() + " entities");
        return new LoadResult(elements, state);
    }

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    /**
     * Reads a resource file from the classpath and returns its contents as a string.
     * Falls back to reading from the filesystem relative to the working directory if
     * the classpath lookup fails.
     *
     * @param path the resource path (e.g. "levels/level_01.json")
     * @return the file contents as a string, or {@code null} if not found
     */
    private String readResourceFile(String path) {
        // Try classpath first
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is == null) {
            // Fall back to filesystem relative to working directory
            java.io.File file = new java.io.File("resources/" + path);
            if (!file.exists()) {
                file = new java.io.File("resources", path);
            }
            if (!file.exists()) {
                System.err.println("LevelLoader: resource not found: " + path);
                return null;
            }
            try {
                is = new java.io.FileInputStream(file);
            } catch (java.io.FileNotFoundException e) {
                System.err.println("LevelLoader: cannot open file: " + file.getPath());
                return null;
            }
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            System.err.println("LevelLoader: I/O error reading " + path + ": " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static Platform.PlatformType parsePlatformType(String s) {
        try {
            return Platform.PlatformType.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Platform.PlatformType.BRICK;
        }
    }

    private static LoreFragment.AbilityUnlock parseAbilityUnlock(String s) {
        try {
            return LoreFragment.AbilityUnlock.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LoreFragment.AbilityUnlock.NONE;
        }
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultVal;
    }

    private static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        if (val instanceof String) {
            return (String) val;
        }
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getArray(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List) {
            return (List<Map<String, Object>>) val;
        }
        return new ArrayList<>();
    }

    // =========================================================================
    // Static inner class — JSONParser
    // =========================================================================

    /**
     * A lightweight, dependency-free JSON parser that handles the subset of JSON used
     * by level data files: flat objects, arrays of objects, and up to two levels of
     * nesting. Supports strings, numbers (integer and floating-point), booleans, null,
     * objects, and arrays. Not intended as a general-purpose JSON library.
     */
    static class JSONParser {

        private final String src;
        private int pos;

        private JSONParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        // ----- Public entry points -----

        /**
         * Parses a JSON object string into a {@code Map<String, Object>}.
         *
         * @param json the JSON text to parse
         * @return a map representing the parsed object
         * @throws IllegalArgumentException if the JSON is malformed
         */
        public static Map<String, Object> parseObject(String json) {
            JSONParser parser = new JSONParser(json);
            parser.skipWhitespace();
            Map<String, Object> result = parser.readObject();
            return result;
        }

        // ----- Core readers -----

        private Object readValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw error("Unexpected end of input");
            }
            char c = src.charAt(pos);
            if (c == '"') return readString();
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == 't' || c == 'f') return readBoolean();
            if (c == 'n') return readNull();
            if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
            throw error("Unexpected character: " + c);
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new HashMap<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                if (pos >= src.length()) break;
                char c = src.charAt(pos);
                if (c == '}') { pos++; break; }
                if (c == ',') { pos++; continue; }
                throw error("Expected ',' or '}' but got: " + c);
            }
            return map;
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = readValue();
                list.add(value);
                skipWhitespace();
                if (pos >= src.length()) break;
                char c = src.charAt(pos);
                if (c == ']') { pos++; break; }
                if (c == ',') { pos++; continue; }
                throw error("Expected ',' or ']' but got: " + c);
            }
            return list;
        }

        private String readString() {
            skipWhitespace();
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') {
                    pos++;
                    return sb.toString();
                }
                if (c == '\\') {
                    pos++;
                    if (pos >= src.length()) throw error("Unexpected end of string escape");
                    char esc = src.charAt(pos);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 >= src.length()) throw error("Incomplete unicode escape");
                            String hex = src.substring(pos + 1, pos + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
                pos++;
            }
            throw error("Unterminated string");
        }

        private Number readNumber() {
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') pos++;
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            String numStr = src.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            } else {
                long val = Long.parseLong(numStr);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                return val;
            }
        }

        private Boolean readBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("Expected boolean");
        }

        private Object readNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Expected null");
        }

        // ----- Utilities -----

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != expected) {
                char actual = pos < src.length() ? src.charAt(pos) : '\0';
                throw error("Expected '" + expected + "' but got '" + actual + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String msg) {
            int context = Math.min(pos + 20, src.length());
            String snippet = src.substring(pos, context).replace("\n", "\\n");
            return new IllegalArgumentException(
                    "JSON parse error at position " + pos + ": " + msg + " near: \"" + snippet + "\"");
        }
    }
}
