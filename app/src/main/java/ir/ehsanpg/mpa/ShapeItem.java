// app/src/main/java/ir/ehsanpg/mpa/ShapeItem.java
package ir.ehsanpg.mpa;

import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

public class ShapeItem {
    public ToolType type;
    public Path path;           // PEN/ERASER
    public RectF bounds;        // RECT/CIRCLE
    public PointF p1, p2, p3;   // TRIANGLE
    public String text;         // TEXT
    public PointF textPos;
    public Paint paint;
    public boolean fill;        // برای پر کردن شکل‌ها

    public ShapeItem(ToolType type, Paint p, boolean fill) {
        this.type = type;
        this.paint = p;
        this.fill = fill;
    }
}