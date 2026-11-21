package com.mejorequipo.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;

import com.mejorequipo.engine.Dex;
import com.mejorequipo.engine.ItemAdvisor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Utilidades visuales: sprites, colores de tipo, anillo de puntuación y layout fluido. */
final class Ui {
    private Ui() {}

    static final int[] TYPE_COLORS = {0xFFA8A878, 0xFFF08030, 0xFF6890F0, 0xFFF8D030, 0xFF78C850, 0xFF98D8D8,
            0xFFC03028, 0xFFA040A0, 0xFFE0C068, 0xFFA890F0, 0xFFF85888, 0xFFA8B820, 0xFFB8A038, 0xFF705898,
            0xFF7038F8, 0xFF705848, 0xFFB8B8D0, 0xFFEE99AC};

    static int scoreColor(double s) {
        if (s >= 85) return 0xFF6EE7A0;
        if (s >= 75) return 0xFFA3E635;
        if (s >= 65) return 0xFFFFD166;
        if (s >= 55) return 0xFFFFA24D;
        return 0xFFFF6B6B;
    }

    // ------------------------------------------------------------------ sprites
    static final class Sprites {
        private final AssetManager am;
        private final Map<String, String> pokeIdx = new HashMap<>(), itemIdx = new HashMap<>();
        private final Map<String, Bitmap> cache = new HashMap<>();

        Sprites(Context c) {
            am = c.getAssets();
            load("sprites/pokemon.idx", pokeIdx);
            load("sprites/items.idx", itemIdx);
        }

        private void load(String path, Map<String, String> into) {
            try (InputStream in = am.open(path)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                for (String line : new String(out.toByteArray(), "UTF-8").split("\n")) {
                    String[] f = line.split("\t");
                    if (f.length == 2) into.put(f[0], f[1]);
                }
            } catch (Exception ignored) {
            }
        }

        private Bitmap decode(String path) {
            Bitmap b = cache.get(path);
            if (b != null) return b;
            try (InputStream in = am.open(path)) {
                b = BitmapFactory.decodeStream(in);
                if (b != null) cache.put(path, b);
            } catch (Exception ignored) {
            }
            return b;
        }

        Bitmap pokemon(Dex.Species sp) {
            String num = pokeIdx.get(sp.id);
            if (num == null && !sp.baseSpecies.isEmpty()) num = pokeIdx.get(sp.baseSpecies);
            if (num == null && sp.name.contains("-")) num = pokeIdx.get(Dex.toId(sp.name.substring(0, sp.name.indexOf('-'))));
            return num == null ? null : decode("sprites/pokemon/" + num + ".png");
        }

        Bitmap item(String itemName) {
            String file = itemIdx.get(ItemAdvisor.canon(itemName));
            return file == null ? null : decode("sprites/items/" + file + ".png");
        }
    }

    // ------------------------------------------------------------------ anillo de puntuación
    static final class ScoreRing extends View {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), fg = new Paint(Paint.ANTI_ALIAS_FLAG),
                tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private double score;

        ScoreRing(Context c, double score) {
            super(c);
            this.score = score;
            float d = c.getResources().getDisplayMetrics().density;
            bg.setStyle(Paint.Style.STROKE);
            bg.setStrokeWidth(5 * d);
            bg.setColor(0xFF2E3140);
            fg.setStyle(Paint.Style.STROKE);
            fg.setStrokeWidth(5 * d);
            fg.setStrokeCap(Paint.Cap.ROUND);
            fg.setColor(scoreColor(score));
            tp.setColor(0xFFECEFF4);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTypeface(Typeface.DEFAULT_BOLD);
            tp.setTextSize(15 * d);
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = bg.getStrokeWidth();
            r.set(pad, pad, getWidth() - pad, getHeight() - pad);
            c.drawArc(r, 0, 360, false, bg);
            c.drawArc(r, -90, (float) (360 * Math.min(100, score) / 100), false, fg);
            c.drawText(String.valueOf((int) Math.round(score)), getWidth() / 2f,
                    getHeight() / 2f - (tp.descent() + tp.ascent()) / 2f, tp);
        }
    }

    // ------------------------------------------------------------------ layout fluido (chips de movimientos)
    static final class Flow extends ViewGroup {
        private final int gap;

        Flow(Context c, int gapPx) {
            super(c);
            gap = gapPx;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec);
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                v.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                if (x > 0 && x + w > maxW) { x = 0; y += rowH + gap; rowH = 0; }
                x += w + gap;
                rowH = Math.max(rowH, h);
            }
            setMeasuredDimension(maxW, y + rowH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l;
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
                if (x > 0 && x + w > maxW) { x = 0; y += rowH + gap; rowH = 0; }
                v.layout(x, y, x + w, y + h);
                x += w + gap;
                rowH = Math.max(rowH, h);
            }
        }
    }

    // ------------------------------------------------------------------ barras de estadísticas base
    static final class StatBars extends View {
        private static final String[] LABELS = {"PS", "Atq", "Def", "AtE", "DeE", "Vel"};
        private final int[] base;
        private final boolean[] used;
        private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d;

        StatBars(Context c, int[] base, boolean[] used) {
            super(c);
            this.base = base;
            this.used = used;
            d = c.getResources().getDisplayMetrics().density;
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(9 * d);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), (int) (54 * d));
        }

        @Override
        protected void onDraw(Canvas c) {
            float colW = getWidth() / 6f, top = 12 * d, bottom = getHeight() - 12 * d;
            for (int i = 0; i < 6; i++) {
                float cx = colW * i + colW / 2, bw = colW * 0.42f;
                float frac = Math.min(1f, base[i] / 160f);
                bar.setColor(!used[i] ? 0xFF3A3E4C : base[i] >= 100 ? 0xFF6EE7A0 : base[i] >= 70 ? 0xFFFFD166 : 0xFFFF8A5B);
                r.set(cx - bw / 2, bottom - (bottom - top) * frac, cx + bw / 2, bottom);
                c.drawRoundRect(r, 3 * d, 3 * d, bar);
                tp.setColor(used[i] ? 0xFFECEFF4 : 0xFF6B7080);
                tp.setFakeBoldText(true);
                c.drawText(String.valueOf(base[i]), cx, r.top - 2 * d, tp);
                tp.setColor(0xFF9AA3B2);
                tp.setFakeBoldText(false);
                c.drawText(LABELS[i], cx, getHeight() - 2 * d, tp);
            }
        }
    }
}
