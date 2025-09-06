// app/src/main/java/ir/ehsanpg/mpa/DrawingView.java
package ir.ehsanpg.mpa;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class DrawingView extends View {
    private ToolType currentTool = ToolType.PEN;
    private int currentColor = Color.BLACK;
    private float currentStroke = 12f;
    private boolean currentFill = false;

    private final List<ShapeItem> items = new ArrayList<>();
    private final Deque<ShapeItem> undoStack = new ArrayDeque<>();

    private ShapeItem currentItem;

    // پس‌زمینه
    private Bitmap backgroundBitmap;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    // زوم و پن
    private final Matrix viewMatrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private float scaleFactor = 1f;
    private float minScale = 0.5f, maxScale = 4f;
    private float lastX, lastY;
    private boolean isPanning = false;
    private ScaleGestureDetector scaleDetector;

    public DrawingView(Context c) { super(c); init(c); }
    public DrawingView(Context c, AttributeSet a) { super(c, a); init(c); }
    public DrawingView(Context c, AttributeSet a, int s) { super(c, a, s); init(c); }

    private void init(Context ctx) {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        scaleDetector = new ScaleGestureDetector(ctx, new ScaleListener());
    }

    private Paint makePaint(boolean erase, boolean fill) {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setDither(true);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(currentStroke);
        if (erase) {
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            p.setColor(Color.TRANSPARENT);
            p.setStyle(Paint.Style.STROKE);
        } else {
            p.setColor(currentColor);
            p.setStyle(fill ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
        }
        return p;
    }

    public void setTool(ToolType t) { this.currentTool = t; }
    public void setColor(int color) { this.currentColor = color; }
    public void setStroke(float width) { this.currentStroke = width; }
    public void setFill(boolean fill) { this.currentFill = fill; }

    public void clearAll() {
        items.clear();
        undoStack.clear();
        invalidate();
    }

    public void undo() {
        if (!items.isEmpty()) {
            undoStack.push(items.remove(items.size()-1));
            invalidate();
        }
    }

    public void redo() {
        if (!undoStack.isEmpty()) {
            items.add(undoStack.pop());
            invalidate();
        }
    }

    public void setBackgroundBitmap(Bitmap bmp) {
        this.backgroundBitmap = bmp;
        invalidate();
    }

    public void addTextAt(String text, float x, float y) {
        Paint p = makePaint(false, false);
        p.setTextSize(Math.max(36f, currentStroke * 4f));
        ShapeItem si = new ShapeItem(ToolType.TEXT, p, false);
        si.text = text;
        PointF pos = screenToCanvas(new PointF(x, y));
        si.textPos = pos;
        items.add(si);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.concat(viewMatrix);

        if (backgroundBitmap != null) {
            Rect src = new Rect(0, 0, backgroundBitmap.getWidth(), backgroundBitmap.getHeight());
            Rect dst = new Rect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(backgroundBitmap, src, dst, bitmapPaint);
        } else {
            canvas.drawColor(Color.WHITE);
        }

        for (ShapeItem si : items) drawItem(canvas, si);
        if (currentItem != null) drawItem(canvas, currentItem);

        canvas.restore();
    }

    private void drawItem(Canvas c, ShapeItem si) {
        switch (si.type) {
            case PEN:
            case ERASER:
                if (si.path != null) c.drawPath(si.path, si.paint);
                break;
            case RECTANGLE:
                if (si.bounds != null) c.drawRect(si.bounds, si.paint);
                break;
            case CIRCLE:
                if (si.bounds != null) c.drawOval(si.bounds, si.paint);
                break;
            case TRIANGLE:
                if (si.p1 != null && si.p2 != null && si.p3 != null) {
                    Path path = new Path();
                    path.moveTo(si.p1.x, si.p1.y);
                    path.lineTo(si.p2.x, si.p2.y);
                    path.lineTo(si.p3.x, si.p3.y);
                    path.close();
                    c.drawPath(path, si.paint);
                }
                break;
            case TEXT:
                if (si.text != null && si.textPos != null) {
                    c.drawText(si.text, si.textPos.x, si.textPos.y, si.paint);
                }
                break;
        }
    }

    private PointF startP, lastP;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);

        final int pointerCount = e.getPointerCount();
        if (pointerCount == 2) {
            isPanning = true;
        }

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isPanning = false;
                lastX = e.getX();
                lastY = e.getY();

                if (currentTool == ToolType.TEXT) return true;

                startP = screenToCanvas(new PointF(e.getX(), e.getY()));
                lastP = new PointF(startP.x, startP.y);

                if (currentTool == ToolType.PEN || currentTool == ToolType.ERASER) {
                    currentItem = new ShapeItem(currentTool, makePaint(currentTool == ToolType.ERASER, false), false);
                    currentItem.path = new Path();
                    currentItem.path.moveTo(startP.x, startP.y);
                } else if (currentTool == ToolType.RECTANGLE || currentTool == ToolType.CIRCLE || currentTool == ToolType.TRIANGLE) {
                    currentItem = new ShapeItem(currentTool, makePaint(false, currentFill), currentFill);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isPanning && pointerCount >= 2) {
                    float dx = e.getX() - lastX;
                    float dy = e.getY() - lastY;
                    viewMatrix.postTranslate(dx, dy);
                    lastX = e.getX();
                    lastY = e.getY();
                    invalidate();
                    return true;
                }

                if (currentTool == ToolType.TEXT || currentItem == null) return true;

                PointF cur = screenToCanvas(new PointF(e.getX(), e.getY()));
                if (currentTool == ToolType.PEN || currentTool == ToolType.ERASER) {
                    float dx = Math.abs(cur.x - lastP.x);
                    float dy = Math.abs(cur.y - lastP.y);
                    if (dx >= 2 || dy >= 2) {
                        currentItem.path.quadTo(lastP.x, lastP.y, (cur.x + lastP.x) / 2f, (cur.y + lastP.y) / 2f);
                        lastP.set(cur.x, cur.y);
                    }
                } else if (currentTool == ToolType.RECTANGLE || currentTool == ToolType.CIRCLE) {
                    currentItem.bounds = new RectF(
                            Math.min(startP.x, cur.x),
                            Math.min(startP.y, cur.y),
                            Math.max(startP.x, cur.x),
                            Math.max(startP.y, cur.y)
                    );
                } else if (currentTool == ToolType.TRIANGLE) {
                    PointF p1 = startP;
                    PointF p2 = cur;
                    float midX = (p1.x + p2.x) / 2f;
                    float height = Math.abs(p2.y - p1.y);
                    float dir = (p2.y > p1.y) ? 1f : -1f;
                    PointF p3 = new PointF(midX, p1.y - dir * height);
                    currentItem.p1 = new PointF(p1.x, p2.y);
                    currentItem.p2 = new PointF(p2.x, p2.y);
                    currentItem.p3 = p3;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (currentItem != null && !isPanning) {
                    if (currentTool == ToolType.PEN || currentTool == ToolType.ERASER) {
                        PointF end = screenToCanvas(new PointF(e.getX(), e.getY()));
                        currentItem.path.lineTo(end.x, end.y);
                    }
                    items.add(currentItem);
                    currentItem = null;
                    invalidate();
                }
                isPanning = false;
                return true;
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            getMatrixValues();
            float current = matrixValues[Matrix.MSCALE_X];
            float target = current * scale;
            if (target < minScale) scale = minScale / current;
            if (target > maxScale) scale = maxScale / current;
            viewMatrix.postScale(scale, scale, detector.getFocusX(), detector.getFocusY());
            invalidate();
            return true;
        }
    }

    private void getMatrixValues() {
        viewMatrix.getValues(matrixValues);
        scaleFactor = matrixValues[Matrix.MSCALE_X];
    }

    public void resetZoom() {
        viewMatrix.reset();
        invalidate();
    }

    private PointF screenToCanvas(PointF p) {
        Matrix inv = new Matrix();
        viewMatrix.invert(inv);
        float[] pts = new float[]{p.x, p.y};
        inv.mapPoints(pts);
        return new PointF(pts[0], pts[1]);
    }

    public Bitmap renderToBitmap() {
        // خروجی در اندازه نمای فعلی
        Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        draw(canvas); // draw با ماتریکس اعمال‌شده
        return bmp;
    }
}