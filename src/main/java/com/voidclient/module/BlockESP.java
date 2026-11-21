package com.voidclient.module;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * BlockESP — วาดกรอบ 3D (fill + outline) และเส้น Tracer รอบบล็อกที่เลือก
 *
 * Performance:
 *  - สแกนแบบแบ่งเวลา (time-sliced) ไม่เกิน ~1.2ms ต่อ tick, เรียงจาก chunk ใกล้ไปไกล
 *  - ใช้ ChunkSection.hasAny (palette check) ข้าม section ที่ไม่มีบล็อกเป้าหมายแบบแทบไม่เสียเวลา
 *  - สแกนลง buffer ใหม่ แล้วค่อยสลับ (ไม่กระพริบ), รีสแกนทุก ~2 วินาที
 *  - จำกัดจำนวนที่เก็บ/วาด (MAX_STORE / MAX_DRAW) เลือกอันที่ใกล้ที่สุดก่อน
 *
 * Config: บล็อกที่เลือกเก็บใน config/voidclient_blockesp.txt (บรรทัดละ 1 id)
 *         ส่วนสี/ระยะ/Tracer เก็บใน state ของโมดูลตามปกติ
 */
public final class BlockESP extends Module {

    public record BlockInfo(String id, String name, String key) {}

    private static final int MAX_STORE = 6000;
    private static final int MAX_DRAW = 800;
    private static final int MAX_TRACERS = 128;
    private static final long BUDGET_NS = 1_200_000L;
    private static final int RESCAN_TICKS = 40;
    private static final int REBUILD_TICKS = 5;

    private static BlockESP INSTANCE;
    private static boolean hooked;
    private static List<BlockInfo> CATALOG;

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("voidclient_blockesp.txt");
    private final LinkedHashSet<String> ids = new LinkedHashSet<>();
    private Set<Block> blocks = Set.of();

    // scan state
    private Long2ObjectOpenHashMap<long[]> results = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<long[]> pending;
    private int[] qx = new int[0], qz = new int[0];
    private int qi, stored, scanPy, scanRange;
    private boolean scanning, dirty = true;
    private int tickCount, lastScanEnd, lastRebuild;
    private Object lastWorld;
    private final long[] scratch = new long[4096];

    // draw list (rebuilt every few ticks)
    private final long[] candBuf = new long[MAX_STORE + 16];
    private final int[] d2Buf = new int[MAX_STORE + 16];
    private final long[] draw = new long[MAX_DRAW];
    private int drawN;

    private int renderErrors;

    public BlockESP() {
        super("blockesp", "Block ESP", "กรอบ 3D + Tracer รอบบล็อกที่เลือก (กด Opt > เพื่อเลือกบล็อก)",
                Category.VISUAL, false, 0, 0);
        with(new Setting("range", "Range", 8f, 128f, 64f),
             new Setting("r", "Red", 0f, 1f, 0.70f),
             new Setting("g", "Green", 0f, 1f, 0.30f),
             new Setting("b", "Blue", 0f, 1f, 1.00f),
             new Setting("a", "Alpha", 0f, 1f, 0.25f));
        INSTANCE = this;
        loadBlocks();
        if (!hooked) {
            hooked = true;
            WorldRenderEvents.END_MAIN.register(ctx -> {
                BlockESP e = INSTANCE;
                if (e != null) e.renderWorld(ctx);
            });
        }
    }

    // =====================================================================
    //  catalog + selection (used by VoidScreen)
    // =====================================================================

    public static synchronized List<BlockInfo> catalog() {
        if (CATALOG == null) {
            List<BlockInfo> l = new ArrayList<>();
            for (Block b : Registries.BLOCK) {
                Identifier id = Registries.BLOCK.getId(b);
                String path = id.getPath();
                if (path.equals("air") || path.equals("cave_air") || path.equals("void_air")) continue;
                String name = b.getName().getString();
                l.add(new BlockInfo(id.toString(), name, (id + " " + name).toLowerCase(Locale.ROOT)));
            }
            l.sort(Comparator.comparing(BlockInfo::id));
            CATALOG = l;
        }
        return CATALOG;
    }

    public boolean has(String id) { return ids.contains(id); }
    public int selectedCount() { return ids.size(); }

    public void toggleBlock(String id) {
        if (!ids.remove(id)) ids.add(id);
        applySelection();
    }

    public void clearBlocks() {
        ids.clear();
        applySelection();
    }

    public boolean tracerOn() { return val("tracer", 1f) >= 0.5f; }

    private int range() { return Math.max(8, Math.min(128, Math.round(val("range", 64f)))); }

    private void applySelection() {
        resolve();
        saveBlocks();
        dirty = true;
    }

    private void resolve() {
        Set<Block> s = new HashSet<>();
        for (String id : ids) {
            Identifier i = Identifier.tryParse(id);
            if (i != null && Registries.BLOCK.containsId(i)) s.add(Registries.BLOCK.get(i));
        }
        blocks = s;
    }

    private void loadBlocks() {
        try {
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (!t.isEmpty()) ids.add(t);
                }
            }
        } catch (IOException e) {
            System.err.println("[VOID] BlockESP load failed: " + e);
        }
        resolve();
    }

    private void saveBlocks() {
        try {
            Files.write(file, ids, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[VOID] BlockESP save failed: " + e);
        }
    }

    // =====================================================================
    //  Module hooks
    // =====================================================================

    @Override public void onToggle(boolean on) {
        dirty = true;
        if (!on) clearAll();
    }

    @Override public void onSetting(String k, float v) {
        if (k.equals("range")) dirty = true;
    }

    private void clearAll() {
        results = new Long2ObjectOpenHashMap<>();
        pending = null;
        scanning = false;
        drawN = 0;
    }

    @Override public void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { clearAll(); lastWorld = null; return; }
        if (mc.world != lastWorld) { lastWorld = mc.world; clearAll(); dirty = true; }

        Set<Block> set = blocks;
        if (set.isEmpty()) { if (drawN != 0 || !results.isEmpty()) clearAll(); return; }

        tickCount++;
        boolean finished = false;

        if (!scanning && (dirty || tickCount - lastScanEnd >= RESCAN_TICKS)) startScan(mc);
        if (scanning) finished = continueScan(mc, set);

        if (finished || tickCount - lastRebuild >= REBUILD_TICKS) {
            lastRebuild = tickCount;
            rebuildDrawList(mc, set);
        }
    }

    // =====================================================================
    //  scanning (time-sliced)
    // =====================================================================

    private void startScan(MinecraftClient mc) {
        int range = range();
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        int n = (range >> 4) + 1;
        List<int[]> cs = new ArrayList<>();
        for (int dx = -n; dx <= n; dx++)
            for (int dz = -n; dz <= n; dz++) {
                int d = dx * dx + dz * dz;
                if (d <= (n + 1) * (n + 1)) cs.add(new int[]{pcx + dx, pcz + dz, d});
            }
        cs.sort(Comparator.comparingInt(a -> a[2]));
        qx = new int[cs.size()];
        qz = new int[cs.size()];
        for (int i = 0; i < qx.length; i++) { qx[i] = cs.get(i)[0]; qz[i] = cs.get(i)[1]; }
        qi = 0;
        stored = 0;
        pending = new Long2ObjectOpenHashMap<>();
        scanPy = mc.player.getBlockY();
        scanRange = range;
        scanning = true;
        dirty = false;
    }

    /** @return true when the scan finished during this call */
    private boolean continueScan(MinecraftClient mc, Set<Block> set) {
        ClientWorld w = mc.world;
        Predicate<BlockState> pred = st -> set.contains(st.getBlock());
        long deadline = System.nanoTime() + BUDGET_NS;

        while (qi < qx.length) {
            if (System.nanoTime() > deadline) return false;
            int cx = qx[qi], cz = qz[qi];
            qi++;
            if (stored >= MAX_STORE) { qi = qx.length; break; }
            WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
            if (ch != null) scanChunk(ch, cx, cz, pred);
        }

        results = pending;
        pending = null;
        scanning = false;
        lastScanEnd = tickCount;
        return true;
    }

    private void scanChunk(WorldChunk ch, int cx, int cz, Predicate<BlockState> pred) {
        ChunkSection[] secs = ch.getSectionArray();
        int bottom = ch.getBottomSectionCoord();
        for (int i = 0; i < secs.length; i++) {
            if (stored >= MAX_STORE) return;
            ChunkSection s = secs[i];
            if (s == null || s.isEmpty()) continue;
            int sy = bottom + i;
            int minY = sy << 4;
            if (minY + 16 < scanPy - scanRange || minY > scanPy + scanRange) continue;
            if (!s.hasAny(pred)) continue;

            int n = 0;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        if (pred.test(s.getBlockState(x, y, z))) {
                            scratch[n++] = BlockPos.asLong((cx << 4) + x, minY + y, (cz << 4) + z);
                        }
                    }
            if (n == 0) continue;
            n = Math.min(n, MAX_STORE - stored);
            pending.put(ChunkSectionPos.asLong(cx, sy, cz), Arrays.copyOf(scratch, n));
            stored += n;
        }
    }

    // =====================================================================
    //  draw list: range filter + validate block still there + nearest N
    // =====================================================================

    private void rebuildDrawList(MinecraftClient mc, Set<Block> set) {
        ClientWorld w = mc.world;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        int range = range();
        double r2 = (double) range * range;
        BlockPos.Mutable mp = new BlockPos.Mutable();
        int n = 0;

        for (long[] arr : results.values()) {
            for (long packed : arr) {
                if (n >= candBuf.length) break;
                int x = BlockPos.unpackLongX(packed), y = BlockPos.unpackLongY(packed), z = BlockPos.unpackLongZ(packed);
                double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > r2) continue;
                mp.set(x, y, z);
                if (!set.contains(w.getBlockState(mp).getBlock())) continue;
                candBuf[n] = packed;
                d2Buf[n] = (int) d2;
                n++;
            }
        }

        if (n <= MAX_DRAW) {
            System.arraycopy(candBuf, 0, draw, 0, n);
            drawN = n;
            return;
        }
        int[] tmp = Arrays.copyOf(d2Buf, n);
        Arrays.sort(tmp);
        int th = tmp[MAX_DRAW - 1];
        int out = 0;
        for (int i = 0; i < n && out < MAX_DRAW; i++) {
            if (d2Buf[i] <= th) draw[out++] = candBuf[i];
        }
        drawN = out;
    }

    // =====================================================================
    //  rendering (Fabric WorldRenderEvents)
    // =====================================================================

    private void renderWorld(WorldRenderContext ctx) {
        if (!enabled() || drawN == 0) return;
        try {
            doRender(ctx);
        } catch (Throwable t) {
            if (++renderErrors == 1) System.err.println("[VOID] BlockESP render error: " + t);
            if (renderErrors >= 5) {
                System.err.println("[VOID] BlockESP auto-disabled after repeated render errors");
                setEnabled(false);
            }
        }
    }

    private void doRender(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        MatrixStack ms = ctx.matrices();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        Matrix4f m = ms.peek().getPositionMatrix();

        float r = val("r", 0.70f), g = val("g", 0.30f), b = val("b", 1.00f), a = val("a", 0.25f);
        boolean tracer = tracerOn();
        int fillCol = argb(r, g, b, a);
        int lineCol = argb(r, g, b, 0.90f);

        // ทุกอย่างวาดเป็น quad ใน layer เดียว (ไม่พึ่ง VertexRendering / lines layer ที่เปลี่ยนใน 1.21.11)
        VertexConsumer vc = vcp.getBuffer(RenderLayers.getDebugQuads());

        Vec3d look = mc.player.getRotationVec(1f);
        float ox = (float) look.x * 0.5f, oy = (float) look.y * 0.5f, oz = (float) look.z * 0.5f;

        final float e = 0.002f;
        int tracers = 0;
        for (int i = 0; i < drawN; i++) {
            long p = draw[i];
            float x0 = (float) (BlockPos.unpackLongX(p) - cam.x) - e;
            float y0 = (float) (BlockPos.unpackLongY(p) - cam.y) - e;
            float z0 = (float) (BlockPos.unpackLongZ(p) - cam.z) - e;
            float x1 = x0 + 1 + 2 * e, y1 = y0 + 1 + 2 * e, z1 = z0 + 1 + 2 * e;

            float cx = (x0 + x1) * 0.5f, cy = (y0 + y1) * 0.5f, cz = (z0 + z1) * 0.5f;
            float dist = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            float hw = 0.012f + 0.0022f * dist;

            if (a > 0.005f) {
                // down, up, north, south, west, east (CCW เมื่อมองจากข้างนอก)
                quad(vc, m, fillCol, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
                quad(vc, m, fillCol, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
                quad(vc, m, fillCol, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
                quad(vc, m, fillCol, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
                quad(vc, m, fillCol, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
                quad(vc, m, fillCol, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
            }

            // outline: 12 edges เป็น ribbon หันเข้าหากล้อง
            for (int yi = 0; yi < 2; yi++) {
                float yy = yi == 0 ? y0 : y1;
                for (int zi = 0; zi < 2; zi++) {
                    float zz = zi == 0 ? z0 : z1;
                    ribbon(vc, m, lineCol, x0, yy, zz, x1, yy, zz, hw);
                }
            }
            for (int xi = 0; xi < 2; xi++) {
                float xx = xi == 0 ? x0 : x1;
                for (int zi = 0; zi < 2; zi++) {
                    float zz = zi == 0 ? z0 : z1;
                    ribbon(vc, m, lineCol, xx, y0, zz, xx, y1, zz, hw);
                }
                for (int yi = 0; yi < 2; yi++) {
                    float yy = yi == 0 ? y0 : y1;
                    ribbon(vc, m, lineCol, xx, yy, z0, xx, yy, z1, hw);
                }
            }

            if (tracer && tracers < MAX_TRACERS) {
                tracers++;
                ribbon(vc, m, lineCol, ox, oy, oz, cx, cy, cz, hw);
            }
        }
    }

    private static int argb(float r, float g, float b, float a) {
        return (Math.round(clamp(a) * 255) << 24) | (Math.round(clamp(r) * 255) << 16)
                | (Math.round(clamp(g) * 255) << 8) | Math.round(clamp(b) * 255);
    }

    private static float clamp(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    private static void vert(VertexConsumer vc, Matrix4f m, int col, float x, float y, float z) {
        vc.vertex(m, x, y, z).color(col);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, int col,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vert(vc, m, col, ax, ay, az);
        vert(vc, m, col, bx, by, bz);
        vert(vc, m, col, cx, cy, cz);
        vert(vc, m, col, dx, dy, dz);
    }

    /** เส้นหนา hw*2 หันเข้าหากล้อง (กล้องอยู่ที่ origin ในพิกัดนี้) */
    private static void ribbon(VertexConsumer vc, Matrix4f m, int col,
                               float ax, float ay, float az, float bx, float by, float bz, float hw) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx; // d x mid
        float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6f) return;
        float k = hw / len;
        sx *= k; sy *= k; sz *= k;
        vert(vc, m, col, ax + sx, ay + sy, az + sz);
        vert(vc, m, col, ax - sx, ay - sy, az - sz);
        vert(vc, m, col, bx - sx, by - sy, bz - sz);
        vert(vc, m, col, bx + sx, by + sy, bz + sz);
    }
}
