package com.example.qrscanner;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/** 扫描框遮罩层：左右贴边，上下各留 5% 边距的长方形扫描区；getScanRect() 提供视图坐标 */
public class OverlayView extends View {
    /** 上下边距占本 View 高度的比例（各自） */
    private static final float V_MARGIN_RATIO_y= 0.02f;
    private static final float V_MARGIN_RATIO_x = 0.05f;
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect scanRect = new Rect();

    public OverlayView(Context context) {
        this(context, null);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // 半透明遮罩
        maskPaint.setColor(0x88000000);

        // 白色描边（2dp）
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(context, 2));
        framePaint.setColor(Color.WHITE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 左右不留边距；上下各留 5% 的边距
        int top = (int) (h * V_MARGIN_RATIO_y);
        int bottom = (int) (h * (1f - V_MARGIN_RATIO_y));
        int left = (int) (w * V_MARGIN_RATIO_x);
        int right = (int) (w * (1f - V_MARGIN_RATIO_x));
        scanRect.set(left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (scanRect.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        int left   = scanRect.left;
        int top    = scanRect.top;
        int right  = scanRect.right;
        int bottom = scanRect.bottom;

        // 1) 画四周遮罩（中间不画，保证无遮罩）
        // 上
        canvas.drawRect(0, 0, w, top, maskPaint);
        // 左
        canvas.drawRect(0, top, left, bottom, maskPaint);
        // 右
        canvas.drawRect(right, top, w, bottom, maskPaint);
        // 下
        canvas.drawRect(0, bottom, w, h, maskPaint);

        // 2) 画白色边框
        canvas.drawRect(scanRect, framePaint);
    }

    /** 返回扫描框（视图坐标系） */
    public Rect getScanRect() {
        return new Rect(scanRect);
    }

    private static float dp(Context c, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }
}
