package com.kleva.barcodescanner;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;

public class OverlaidPreviewView extends PreviewView {

    private static float HEIGHT_SCALER = 640;
    private static float WIDTH_SCALER = 480;

    private Bitmap overlayBitmap;
    private RectF overlayRect;
    private RectF testOverlayRect;

    private boolean shouldUpdateOverlay = true;
    private boolean artifactDetected = false;


    public OverlaidPreviewView(@NonNull Context context) {
        super(context);
    }

    public OverlaidPreviewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public OverlaidPreviewView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public OverlaidPreviewView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (overlayBitmap == null || shouldUpdateOverlay) {
            createWindowFrame(); // Lazy creation of the window frame, this is needed as we don't know the width & height of the screen until draw time
            shouldUpdateOverlay = false;
        }
        canvas.drawBitmap(overlayBitmap, 0, 0, null);
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean isClickable() {
        return false;
    }

    protected void createWindowFrame() {
        overlayBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888); // Create a new image we will draw over the map
        Canvas osCanvas = new Canvas(overlayBitmap); // Create a   canvas to draw onto the new image

        RectF outerRectangle = new RectF(0, 0, getWidth(), getHeight());

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); // Anti alias allows for smooth corners
        paint.setColor(artifactDetected ? Color.GREEN : Color.RED); // This is the color of your activity background
        paint.setAlpha(80);
        osCanvas.drawRect(outerRectangle, paint);

        //paint.setColor(Color.TRANSPARENT); // An obvious color to help debugging


        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT)); // A out B http://en.wikipedia.org/wiki/File:Alpha_compositing.svg
        paint.setAlpha(0);

        Log.e("PREVIEVIEW", getWidth() + " " + getHeight());

        float centerX = getWidth() / 2;
        float centerY = getHeight() / 2;
        float squareSide = Math.min(getWidth(), getHeight()) / 2 - 50;

        float heightScaleratio = HEIGHT_SCALER/getHeight();
        float widthScaleRatio = WIDTH_SCALER/getWidth();

        overlayRect = new RectF(
                (centerX - squareSide),
                (centerY - squareSide),
                (centerX + squareSide),
                (centerY + squareSide)
        );

        testOverlayRect = new RectF(
                (centerX - squareSide) * widthScaleRatio,
                (centerY - squareSide) * heightScaleratio,
                (centerX + squareSide) * widthScaleRatio,
                (centerY + squareSide) * heightScaleratio
        );
        osCanvas.drawRoundRect(overlayRect, 20, 20, paint);
    }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        overlayBitmap = null; // If the layout changes null our frame so it can be recreated with the new width and height
    }

    public RectF getOverlayRect() {
        return new RectF(overlayRect);
    }

    public RectF getTestOverlayRect() {
        return testOverlayRect;
    }

    public void updateOverlay(boolean artifactDetected) {

        if(artifactDetected == this.artifactDetected) {
            return;
        }

        this.artifactDetected = artifactDetected;
        shouldUpdateOverlay = true;
        invalidate();
    }
}
