package chenyue.arfyp.helloar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.LinkedList;
import java.util.List;

public class trackingOverlay extends SurfaceView implements SurfaceHolder.Callback {
    private final List<DrawCallback> drawCallbackList = new LinkedList<>();
    private static final String TAG = "tracking view";
    private SurfaceHolder holder;
    private Canvas canvas;

    public trackingOverlay(Context context, AttributeSet attributes) {
        super(context, attributes);
        holder = getHolder();
        setZOrderMediaOverlay(true);
        holder.setFormat(PixelFormat.TRANSLUCENT);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void addDrawCallback(final DrawCallback drawCallback) {
        drawCallbackList.add(drawCallback);
    }

    public interface DrawCallback {
        void draw(final Canvas canvas);

    }

    public void refreshView() {
        try {
            canvas = holder.lockCanvas();
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            for (DrawCallback drawCallback : drawCallbackList) {
                drawCallback.draw(canvas);
            }
        } catch (Exception e) {
        } finally {
            if (canvas != null)
                holder.unlockCanvasAndPost(canvas);
        }
    }

}
