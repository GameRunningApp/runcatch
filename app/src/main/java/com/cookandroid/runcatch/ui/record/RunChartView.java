package com.cookandroid.runcatch.ui.record;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.cookandroid.runcatch.R;

import java.util.Arrays;
import java.util.Locale;

public class RunChartView extends View {

    public enum Metric {
        DISTANCE("거리", "거리 (km)", false, 0f, true),
        TIME("시간", "시간 (분)", false, 0f, true),
        PACE("페이스", "페이스 (/km)", true, 10f, false),
        SPEED("속도", "속도 (km/h)", false, 0f, false);

        public final String label;
        final String title;
        final boolean inverted;
        final float minStep;
        final boolean fromZero;

        Metric(String label, String title, boolean inverted, float minStep, boolean fromZero) {
            this.label = label;
            this.title = title;
            this.inverted = inverted;
            this.minStep = minStep;
            this.fromZero = fromZero;
        }
    }

    private static class Axis {
        float lower;
        float upper;
        float step;
        int steps;
    }

    private static final int TARGET_STEPS = 4;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final Axis xAxis = new Axis();
    private final Axis yAxis = new Axis();
    private final float density;
    private final int primaryColor;

    private float[] xValues = new float[0];
    private float[] yValues = new float[0];
    private Metric xMetric = Metric.DISTANCE;
    private Metric yMetric = Metric.PACE;

    public RunChartView(Context context) {
        this(context, null);
    }

    public RunChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        primaryColor = ContextCompat.getColor(context, R.color.primary);

        linePaint.setColor(primaryColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint.setColor(ColorUtils.setAlphaComponent(primaryColor, 200));
        dotPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(ContextCompat.getColor(context, R.color.card_stroke));
        gridPaint.setStrokeWidth(dp(1));

        axisPaint.setColor(ContextCompat.getColor(context, R.color.nav_inactive));
        axisPaint.setStrokeWidth(dp(1));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(sp(10));

        titlePaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        titlePaint.setTextSize(sp(11));
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(float[] xValues, float[] yValues, Metric xMetric, Metric yMetric) {
        int count = Math.min(xValues.length, yValues.length);
        this.xValues = Arrays.copyOf(xValues, count);
        this.yValues = Arrays.copyOf(yValues, count);
        this.xMetric = xMetric;
        this.yMetric = yMetric;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                ColorUtils.setAlphaComponent(primaryColor, 140),
                ColorUtils.setAlphaComponent(primaryColor, 15),
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (xValues.length < 2) {
            canvas.drawText("기록이 없습니다", getWidth() / 2f, getHeight() / 2f, titlePaint);
            return;
        }

        computeAxis(xValues, xMetric, xAxis, true);
        computeAxis(yValues, yMetric, yAxis, false);

        float textHeight = labelPaint.descent() - labelPaint.ascent();
        float labelWidth = 0;
        for (int i = 0; i <= yAxis.steps; i++) {
            labelWidth = Math.max(labelWidth,
                    labelPaint.measureText(formatValue(yMetric, yAxis, yAxis.lower + yAxis.step * i)));
        }

        float left = dp(18) + labelWidth + dp(8);
        float top = textHeight / 2f;
        float right = getWidth() - dp(12);
        float bottom = getHeight() - textHeight * 2 - dp(12);

        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= yAxis.steps; i++) {
            float value = yAxis.lower + yAxis.step * i;
            float y = toY(value, top, bottom);
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawText(formatValue(yMetric, yAxis, value), left - dp(6), y + textHeight / 3f, labelPaint);
        }

        labelPaint.setTextAlign(Paint.Align.CENTER);
        float labelY = bottom + dp(4) + textHeight;
        for (int i = 0; i <= xAxis.steps; i++) {
            float value = xAxis.lower + xAxis.step * i;
            canvas.drawText(formatValue(xMetric, xAxis, value), toX(value, left, right), labelY, labelPaint);
        }

        if (isIncreasing(xValues)) {
            linePath.reset();
            fillPath.reset();
            for (int i = 0; i < xValues.length; i++) {
                float x = toX(xValues[i], left, right);
                float y = toY(yValues[i], top, bottom);
                if (i == 0) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, bottom);
                } else {
                    linePath.lineTo(x, y);
                }
                fillPath.lineTo(x, y);
            }
            fillPath.lineTo(toX(xValues[xValues.length - 1], left, right), bottom);
            fillPath.close();

            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);
        } else {
            for (int i = 0; i < xValues.length; i++) {
                canvas.drawCircle(toX(xValues[i], left, right), toY(yValues[i], top, bottom), dp(4), dotPaint);
            }
        }

        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);

        canvas.drawText(xMetric.title, (left + right) / 2f, getHeight() - dp(2), titlePaint);

        canvas.save();
        canvas.rotate(-90, dp(8), (top + bottom) / 2f);
        canvas.drawText(yMetric.title, dp(8), (top + bottom) / 2f + textHeight / 3f, titlePaint);
        canvas.restore();
    }

    private void computeAxis(float[] values, Metric metric, Axis axis, boolean tight) {
        float minValue = Float.MAX_VALUE;
        float maxValue = -Float.MAX_VALUE;
        for (float value : values) {
            minValue = Math.min(minValue, value);
            maxValue = Math.max(maxValue, value);
        }
        float lowest = metric.fromZero ? 0f : minValue;
        axis.step = niceStep((maxValue - lowest) / TARGET_STEPS, metric.minStep);
        axis.lower = (float) Math.floor(lowest / axis.step) * axis.step;
        if (tight && maxValue > axis.lower) {
            axis.upper = maxValue;
            axis.steps = Math.max(1, (int) Math.floor((maxValue - axis.lower) / axis.step + 0.001f));
        } else {
            axis.steps = Math.max(1, (int) Math.ceil((maxValue - axis.lower) / axis.step - 0.001f));
            axis.upper = axis.lower + axis.step * axis.steps;
        }
    }

    private float toX(float value, float left, float right) {
        float ratio = (value - xAxis.lower) / (xAxis.upper - xAxis.lower);
        return xMetric.inverted
                ? right - ratio * (right - left)
                : left + ratio * (right - left);
    }

    private float toY(float value, float top, float bottom) {
        float ratio = (value - yAxis.lower) / (yAxis.upper - yAxis.lower);
        return yMetric.inverted
                ? top + ratio * (bottom - top)
                : bottom - ratio * (bottom - top);
    }

    private boolean isIncreasing(float[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1]) {
                return false;
            }
        }
        return true;
    }

    private float niceStep(float raw, float minStep) {
        if (minStep > 0) {
            return Math.max(minStep, (float) Math.ceil(raw / minStep) * minStep);
        }
        if (raw <= 0) {
            return 1f;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalized = raw / magnitude;
        double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
        return (float) (nice * magnitude);
    }

    private String formatValue(Metric metric, Axis axis, float value) {
        if (metric == Metric.PACE) {
            int total = Math.round(value);
            return String.format(Locale.US, "%d'%02d\"", total / 60, total % 60);
        }
        if (axis.step >= 1f) {
            return String.format(Locale.US, "%.0f", value);
        }
        if (axis.step >= 0.1f) {
            return String.format(Locale.US, "%.1f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
