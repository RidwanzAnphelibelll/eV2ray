package com.rscoders.v2ray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayDeque;
import java.util.Deque;

public class SpeedGraphView extends View {

    private static final int MAX = 30;
    private final Deque<Long> rxHist = new ArrayDeque<>();
    private final Deque<Long> txHist = new ArrayDeque<>();
    private final Paint rxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rxFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SpeedGraphView(Context ctx) { super(ctx); init(); }
    public SpeedGraphView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        rxPaint.setColor(Color.parseColor("#448AFF"));
        rxPaint.setStyle(Paint.Style.STROKE);
        rxPaint.setStrokeWidth(2.5f);
        rxPaint.setStrokeCap(Paint.Cap.ROUND);
        rxPaint.setStrokeJoin(Paint.Join.ROUND);

        txPaint.setColor(Color.parseColor("#FF5252"));
        txPaint.setStyle(Paint.Style.STROKE);
        txPaint.setStrokeWidth(2.5f);
        txPaint.setStrokeCap(Paint.Cap.ROUND);
        txPaint.setStrokeJoin(Paint.Join.ROUND);

        rxFill.setStyle(Paint.Style.FILL);
        txFill.setStyle(Paint.Style.FILL);
    }

    public void pushData(long rx, long tx) {
        if (rxHist.size() >= MAX) rxHist.poll();
        if (txHist.size() >= MAX) txHist.poll();
        rxHist.offer(rx);
        txHist.offer(tx);
        invalidate();
    }

    public void setData(long[] rx, long[] tx) {
        rxHist.clear();
        txHist.clear();
        if (rx != null) for (long v : rx) rxHist.offer(v);
        if (tx != null) for (long v : tx) txHist.offer(v);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0) {
            rxFill.setShader(new LinearGradient(0, 0, 0, h,
                Color.parseColor("#55448AFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            txFill.setShader(new LinearGradient(0, 0, 0, h,
                Color.parseColor("#55FF5252"), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || rxHist.isEmpty()) return;

        long maxVal = 1024;
        for (long v : rxHist) if (v > maxVal) maxVal = v;
        for (long v : txHist) if (v > maxVal) maxVal = v;
        maxVal = (long) (maxVal * 1.2);

        long[] rx = rxHist.stream().mapToLong(Long::longValue).toArray();
        long[] tx = txHist.stream().mapToLong(Long::longValue).toArray();
        drawCurve(canvas, tx, maxVal, w, h, txPaint, txFill);
        drawCurve(canvas, rx, maxVal, w, h, rxPaint, rxFill);
    }

    private void drawCurve(Canvas canvas, long[] data, long maxVal, int w, int h, Paint line, Paint fill) {
        if (data.length < 2) return;
        float slot = (float) w / (MAX - 1);
        float startX = (MAX - data.length) * slot;

        Path path = new Path();
        Path fillPath = new Path();

        float x0 = startX;
        float y0 = h - (float) data[0] / maxVal * (h - 6);
        path.moveTo(x0, y0);
        fillPath.moveTo(x0, h);
        fillPath.lineTo(x0, y0);

        for (int i = 1; i < data.length; i++) {
            float x1 = startX + i * slot;
            float y1 = h - (float) data[i] / maxVal * (h - 6);
            float cx = (x0 + x1) / 2;
            path.cubicTo(cx, y0, cx, y1, x1, y1);
            fillPath.cubicTo(cx, y0, cx, y1, x1, y1);
            x0 = x1;
            y0 = y1;
        }

        fillPath.lineTo(startX + (data.length - 1) * slot, h);
        fillPath.close();
        canvas.drawPath(fillPath, fill);
        canvas.drawPath(path, line);
    }
}
