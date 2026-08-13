package com.rscoders.v2ray;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
        rxPaint.setColor(Color.parseColor("#2196F3"));
        rxPaint.setStyle(Paint.Style.STROKE);
        rxPaint.setStrokeWidth(3f);
        txPaint.setColor(Color.parseColor("#F44336"));
        txPaint.setStyle(Paint.Style.STROKE);
        txPaint.setStrokeWidth(3f);
        rxFill.setColor(Color.parseColor("#332196F3"));
        rxFill.setStyle(Paint.Style.FILL);
        txFill.setColor(Color.parseColor("#33F44336"));
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || rxHist.isEmpty()) return;
        long maxVal = 1;
        for (long v : rxHist) if (v > maxVal) maxVal = v;
        for (long v : txHist) if (v > maxVal) maxVal = v;
        long[] rx = rxHist.stream().mapToLong(Long::longValue).toArray();
        long[] tx = txHist.stream().mapToLong(Long::longValue).toArray();
        drawLine(canvas, rx, maxVal, w, h, rxPaint, rxFill);
        drawLine(canvas, tx, maxVal, w, h, txPaint, txFill);
    }

    private void drawLine(Canvas canvas, long[] data, long maxVal, int w, int h, Paint line, Paint fill) {
        if (data.length == 0) return;
        float slot = (float) w / (MAX - 1);
        float startX = (MAX - data.length) * slot;
        Path path = new Path();
        Path fillPath = new Path();
        float y0 = h - (float) data[0] / maxVal * (h - 4);
        path.moveTo(startX, y0);
        fillPath.moveTo(startX, h);
        fillPath.lineTo(startX, y0);
        for (int i = 1; i < data.length; i++) {
            float x = startX + i * slot;
            float y = h - (float) data[i] / maxVal * (h - 4);
            path.lineTo(x, y);
            fillPath.lineTo(x, y);
        }
        fillPath.lineTo(startX + (data.length - 1) * slot, h);
        fillPath.close();
        canvas.drawPath(fillPath, fill);
        canvas.drawPath(path, line);
    }
}
