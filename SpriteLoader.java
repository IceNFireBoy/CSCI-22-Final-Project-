/**
 * Singleton image-loading utility that caches every {@link java.awt.image.BufferedImage}
 * by file path so each sprite is read from disk at most once per JVM session. Provides
 * three loading modes: a guaranteed-success {@link #load(String)} that substitutes a
 * magenta 64×64 placeholder when an asset is missing, a null-on-failure
 * {@link #tryLoad(String)} for optional artwork (e.g. cutscene panels), and a
 * {@link #loadScaled(String, int, int)} convenience that downsamples or upsamples the
 * image to an exact pixel size using bilinear interpolation.
 *
 * <p>Architecture role: Every rendering class that needs a bitmap goes through this
 * singleton. {@link Platform#render(Graphics2D)} calls {@link #load(String)} to fetch
 * tile sprites; {@link CutsceneRenderer} calls {@link #tryLoad(String)} so missing panel
 * art falls through to text-only mode; {@link SpriteLoader#loadScaled(String, int, int)}
 * is used by the HUD to draw proportionally resized block-type previews. The HashMap cache
 * eliminates repeated {@link javax.imageio.ImageIO#read} calls, which are slow I/O
 * operations that would otherwise stall the 60 fps render loop.</p>
 *
 * @author [YOUR NAME]
 * @id [YOUR ID]
 * @date 2026-03-21
 * @certification I certify that this code is my own work and has not been copied from
 *                any other source, in whole or in part.
 */
import javax.imageio.ImageIO;                    // Java standard library for reading image files from disk
import java.awt.image.BufferedImage;             // In-memory ARGB pixel buffer used as the canonical image type throughout the project
import java.awt.Graphics2D;                      // 2D drawing context used to fill the magenta placeholder and to draw scaled images
import java.awt.RenderingHints;                  // Controls image scaling quality; we use BILINEAR interpolation for smooth resizing
import java.io.File;                             // Wraps a filesystem path so ImageIO.read() can locate the asset
import java.util.HashMap;                        // Backing store for the image cache; O(1) lookup by path string

public class SpriteLoader { // Singleton — one shared image cache for the entire JVM session; no public constructor

    private static SpriteLoader instance;                              // The single instance; lazily created on first getInstance() call

    private HashMap<String, BufferedImage> cache = new HashMap<>();    // Key = file path (or "try::path" for tryLoad entries); value = cached image or null placeholder

    /**
     * Returns the singleton {@code SpriteLoader} instance, creating it on the first
     * call. Synchronised to prevent a race condition if two threads call this
     * simultaneously during startup (e.g. the game loop thread and the EDT).
     *
     * <p>Architecture role: Every rendering class calls this to obtain the shared loader
     * rather than constructing its own, ensuring the cache is effective across all
     * render passes. Called on every frame by {@link Platform#render(Graphics2D)},
     * {@link Player#render(Graphics2D)}, and the HUD helpers inside
     * {@link GameCanvas}.</p>
     *
     * @return the shared {@code SpriteLoader}; never {@code null}
     */
    public static synchronized SpriteLoader getInstance() { // Thread-safe lazy initialisation — synchronised to prevent duplicate instances at startup
        if (instance == null) instance = new SpriteLoader(); // Create the single instance only on the very first call
        return instance;                                      // Return the cached singleton on all subsequent calls
    }

    private SpriteLoader() {} // Private constructor — prevents external instantiation; use getInstance() instead

    /**
     * Loads and caches the image at {@code path}, returning the cached copy on
     * subsequent calls. If the file is missing, unreadable, or {@code ImageIO.read}
     * returns {@code null}, a 64×64 magenta placeholder is substituted and cached so
     * that subsequent calls for the same missing asset return the placeholder without
     * retrying the disk read.
     *
     * <p>Architecture role: This is the primary loading method used by
     * {@link Platform#render(Graphics2D)} for tile sprites and by
     * {@link Player#render(Graphics2D)} for character animation frames. It guarantees
     * a non-null return, so callers never need null-checks and the renderer never
     * crashes due to a missing asset — it just shows a magenta square instead, which
     * is an obvious "art missing" signal to the developer.</p>
     *
     * <p>Interaction: Results are shared with {@link #loadScaled(String, int, int)},
     * which calls {@code load(path)} first to get the source image before scaling.
     * The {@code "try::"} cache-key prefix used by {@link #tryLoad(String)} is
     * distinct, so the same path can appear under both keys independently.</p>
     *
     * @param path the filesystem path to the image file (relative or absolute);
     *             must not be {@code null}
     * @return the loaded {@link BufferedImage}, or a magenta 64×64 placeholder if
     *         the asset cannot be read; never {@code null}
     */
    public BufferedImage load(String path) {                              // Guaranteed-success image load with magenta placeholder fallback
        if (cache.containsKey(path)) return cache.get(path);             // Cache hit — return the previously loaded (or previously failed) image immediately, skipping disk I/O
        try {
            BufferedImage img = ImageIO.read(new File(path));             // Attempt to read the image from disk using Java's built-in ImageIO; returns null for unsupported formats
            if (img == null) throw new Exception("null result");          // ImageIO.read returns null (not an exception) for unrecognised formats; convert to exception so the catch block handles it
            cache.put(path, img);                                         // Store the successfully loaded image in the cache under its path key so future calls skip disk I/O
            return img;                                                   // Return the freshly loaded image to the caller
        } catch (Exception e) {
            System.out.println("SpriteLoader: missing sprite — " + path); // Log the missing asset so developers can spot missing art at a glance in the console
            BufferedImage placeholder = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB); // Create a 64×64 ARGB buffer — matches the standard tile size so it fits into the rendering slot
            Graphics2D g = placeholder.createGraphics();                  // Obtain a 2D drawing context for the placeholder buffer
            g.setColor(new java.awt.Color(255, 0, 255));                  // Set fill colour to magenta (R=255, G=0, B=255) — vivid, clearly wrong, immediately noticeable in-game
            g.fillRect(0, 0, 64, 64);                                     // Fill the entire 64×64 area with magenta so the missing sprite is visually obvious
            g.dispose();                                                   // Release the Graphics2D context to free native resources; must always be done after use
            cache.put(path, placeholder);                                  // Cache the placeholder so subsequent load() calls for the same missing path return it instantly
            return placeholder;                                            // Return the magenta placeholder to the caller; non-null guarantees the renderer never crashes
        }
    }

    /**
     * Attempts to load the sprite at {@code path} without substituting the magenta
     * placeholder. Returns {@code null} if the file is missing or cannot be decoded,
     * which lets optional-art call sites (e.g. the cutscene renderer) fall through to
     * a text-only presentation instead of flashing a magenta rectangle.
     *
     * <p>Architecture role: Used by {@link CutsceneRenderer} to load panel artwork.
     * When panel art is absent (e.g. during development before all cutscene images
     * ship), the renderer falls back to rendering only the dialogue text, which is
     * always present in {@link CutsceneScript}. Successful loads are cached under a
     * {@code "try::"} prefix so later {@link #load(String)} calls for the same path
     * still get the real image via the normal cache key.</p>
     *
     * <p>The separate cache key ({@code "try::" + path}) ensures that a failed
     * {@code tryLoad} does not pollute the normal {@code load} cache with a null
     * entry — if a build later adds the missing asset, {@link #load(String)} will
     * successfully load it without seeing the cached null from {@code tryLoad}.</p>
     *
     * @param path the filesystem path to the image file; must not be {@code null}
     * @return the loaded {@link BufferedImage}, or {@code null} if the file is
     *         missing or cannot be decoded
     */
    public BufferedImage tryLoad(String path) {                         // Optional-art loader — returns null on failure instead of the magenta placeholder
        String key = "try::" + path;                                    // Use a distinct cache key prefix so null entries don't shadow successful load() results for the same path
        if (cache.containsKey(key)) return cache.get(key);             // Cache hit — return whatever was stored (may be null if the previous attempt failed)
        try {
            File f = new File(path);                                    // Wrap the path in a File to allow file-existence check before the more expensive ImageIO.read call
            if (!f.isFile()) { cache.put(key, null); return null; }    // If the path does not point to a regular file (missing or is a directory), cache null and return immediately
            BufferedImage img = ImageIO.read(f);                        // Attempt to decode the image; may return null for unsupported formats
            if (img == null) { cache.put(key, null); return null; }    // null from ImageIO.read means unsupported format — cache null so we don't retry the expensive decode
            cache.put(key, img);                                        // Successfully loaded — cache the image under the "try::" key for subsequent calls
            return img;                                                 // Return the real image to the caller
        } catch (Exception e) {
            cache.put(key, null);                                       // Any I/O or decoding exception — cache null to prevent repeated failed reads
            return null;                                                 // Return null; caller is responsible for falling back to a no-art rendering path
        }
    }

    /**
     * Loads the image at {@code path} (via {@link #load(String)}) and returns a new
     * {@link BufferedImage} scaled to exactly {@code w} × {@code h} pixels using
     * bilinear interpolation for smooth resizing.
     *
     * <p>Architecture role: Used by the Apprentice HUD inside {@link GameCanvas#renderHUD}
     * to draw the current block-type preview icon at a consistent 40×40 pixel size,
     * regardless of the source tile's native dimensions. Bilinear interpolation
     * ({@link RenderingHints#VALUE_INTERPOLATION_BILINEAR}) produces smoother results
     * than nearest-neighbour at the small icon sizes used in the HUD.</p>
     *
     * <p>Note: The scaled image is NOT cached — each call produces a fresh
     * {@link BufferedImage}. If the same path/size combination is requested every frame
     * by the HUD, the caller should cache the result itself to avoid per-frame
     * allocation.</p>
     *
     * @param path the filesystem path to the source image; must not be {@code null}
     * @param w    the desired output width in pixels; must be positive
     * @param h    the desired output height in pixels; must be positive
     * @return a new {@link BufferedImage} of size {@code w}×{@code h} containing the
     *         scaled source image; never {@code null} (falls back to a scaled magenta
     *         placeholder if the source file is missing)
     */
    public BufferedImage loadScaled(String path, int w, int h) {        // Load-then-scale convenience method: handles both the load and the resize in one call
        BufferedImage src = load(path);                                  // Load (or retrieve from cache) the source image; guaranteed non-null (may be magenta placeholder)
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); // Allocate the output buffer at the requested pixel dimensions with ARGB format for transparency support
        Graphics2D g = scaled.createGraphics();                          // Obtain a 2D context to draw the resized source image into the output buffer
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,             // Set the interpolation quality hint key...
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);               // ...to bilinear mode: averages the four nearest source pixels for smoother results than nearest-neighbour
        g.drawImage(src, 0, 0, w, h, null);                             // Draw the source image into the (0,0,w,h) region of the output buffer; Java scales it to fit during this call
        g.dispose();                                                     // Release the Graphics2D context to free native resources after the single draw operation
        return scaled;                                                   // Return the newly created scaled image to the caller
    }
}
