// app/src/main/java/ir/ehsanpg/mpa/MainActivity.java
package ir.ehsanpg.mpa;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DrawingView drawingView;

    private Button btnPen, btnEraser, btnRect, btnCircle, btnTriangle, btnText,
            btnColor, btnUndo, btnRedo, btnClear, btnSave, btnLoad, btnZoomReset;
    private SeekBar seekThickness;
    private CheckBox cbFill;

    private ToolType currentTool = ToolType.PEN;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawingView = findViewById(R.id.drawingView);

        btnPen = findViewById(R.id.btnPen);
        btnEraser = findViewById(R.id.btnEraser);
        btnRect = findViewById(R.id.btnRect);
        btnCircle = findViewById(R.id.btnCircle);
        btnTriangle = findViewById(R.id.btnTriangle);
        btnText = findViewById(R.id.btnText);
        btnColor = findViewById(R.id.btnColor);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btnClear = findViewById(R.id.btnClear);
        btnSave = findViewById(R.id.btnSave);
        btnLoad = findViewById(R.id.btnLoad);
        btnZoomReset = findViewById(R.id.btnZoomReset);
        seekThickness = findViewById(R.id.seekThickness);
        cbFill = findViewById(R.id.cbFill);

        btnPen.setOnClickListener(v -> setTool(ToolType.PEN));
        btnEraser.setOnClickListener(v -> setTool(ToolType.ERASER));
        btnRect.setOnClickListener(v -> setTool(ToolType.RECTANGLE));
        btnCircle.setOnClickListener(v -> setTool(ToolType.CIRCLE));
        btnTriangle.setOnClickListener(v -> setTool(ToolType.TRIANGLE));
        btnText.setOnClickListener(v -> {
            setTool(ToolType.TEXT);
            Toast.makeText(this, "برای درج متن روی بوم لمس کن", Toast.LENGTH_SHORT).show();
        });

        btnColor.setOnClickListener(v -> showColorPicker());

        cbFill.setOnCheckedChangeListener((buttonView, isChecked) -> drawingView.setFill(isChecked));

        seekThickness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                drawingView.setStroke(Math.max(1f, progress));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnUndo.setOnClickListener(v -> drawingView.undo());
        btnRedo.setOnClickListener(v -> drawingView.redo());
        btnClear.setOnClickListener(v -> drawingView.clearAll());
        btnSave.setOnClickListener(v -> saveToGallery());
        btnLoad.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnZoomReset.setOnClickListener(v -> drawingView.resetZoom());

        // درج متن با لمس
        drawingView.setOnTouchListener((v, e) -> {
            if (currentTool == ToolType.TEXT && e.getAction() == MotionEvent.ACTION_UP) {
                promptTextAt(e.getX(), e.getY());
                return true;
            }
            return false;
        });
    }

    private void setTool(ToolType t) {
        currentTool = t;
        drawingView.setTool(t);
    }

    private void promptTextAt(float x, float y) {
        EditText et = new EditText(this);
        et.setHint(getString(R.string.enter_text));
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.text))
                .setView(et)
                .setPositiveButton(getString(R.string.ok), (d, w) -> {
                    String t = et.getText().toString();
                    if (!t.trim().isEmpty()) drawingView.addTextAt(t, x, y);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showColorPicker() {
        int[] colors = new int[]{
                Color.BLACK, Color.DKGRAY, Color.GRAY, Color.RED,
                Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA
        };
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);

        int size = (int) (40 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);

        for (int c : colors) {
            View v = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            v.setLayoutParams(lp);
            v.setBackgroundColor(c);
            v.setOnClickListener(x -> {
                drawingView.setColor(c);
                Toast.makeText(this, "رنگ تغییر کرد", Toast.LENGTH_SHORT).show();
            });
            layout.addView(v);
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.color))
                .setView(layout)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            Bitmap bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            drawingView.setBackgroundBitmap(bmp);
        } catch (Exception ex) {
            Toast.makeText(this, "خطا در بارگذاری تصویر", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToGallery() {
        if (Build.VERSION.SDK_INT < 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
                return;
            }
        }
        Bitmap bmp = drawingView.renderToBitmap();
        String name = "M_Painting_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/M Painting");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Toast.makeText(this, "ذخیره‌سازی ناموفق", Toast.LENGTH_SHORT).show();
            return;
        }
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            Toast.makeText(this, "ذخیره شد: گالری", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "خطا در ذخیره‌سازی", Toast.LENGTH_SHORT).show();
        }
    }
}