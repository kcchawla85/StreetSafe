
package net.chitholian.potholesdetection.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.List;

import net.chitholian.potholesdetection.Classifier.Recognition;
import net.chitholian.potholesdetection.env.ImageUtils;
import net.chitholian.potholesdetection.env.Logger;
import net.chitholian.potholesdetection.env.BorderedText;


/**
 * A tracker wrapping ObjectTracker that also handles non-max suppression and matching existing
 * objects to new detections.
 */
public class MultiBoxTracker {
  private final Logger logger = new Logger();


  // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
  // the lower scored box (new or old) will be removed.
  private static final float MAX_OVERLAP = 0.2f;

  private static final float MIN_SIZE = 16.0f;

  // Allow replacement of the tracked box with new results if
  // correlation has dropped below this level.
  private static final float MARGINAL_CORRELATION = 0.75f;

  // Consider object to be lost if correlation falls below this threshold.
  private static final float MIN_CORRELATION = 0.4f;

  private static final int COLOR = Color.RED;
  private static final int TEXT_SIZE_DIP = 12;


  public ObjectTracker objectTracker;

  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();

  private static class TrackedRecognition {
    ObjectTracker.TrackedObject trackedObject;
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }

  public final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();

  private final Paint boxPaint = new Paint();
  private final float textSizePx;
  private final BorderedText borderedText;

  private Matrix frameToCanvasMatrix;

  private int frameWidth;
  private int frameHeight;

  private int sensorOrientation;
  private Context context;

  public MultiBoxTracker(final Context context) {
    this.context = context;

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(8.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
    }

    if (objectTracker == null) {
      return;
    }

    final Matrix matrix = getFrameToCanvasMatrix();
    objectTracker.drawDebug(canvas, matrix);
  }

  public synchronized void trackResults(final List<Recognition> results, final byte[] frame, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(timestamp, results, frame);
  }

  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;
    final float multiplier =
            Math.min(canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                    canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
            ImageUtils.getTransformationMatrix(
                    frameWidth,
                    frameHeight,
                    (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                    (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                    sensorOrientation,
                    false);
    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos =
              (objectTracker != null)
                      ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
                      : new RectF(recognition.location);

      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

      final String labelString =
              !TextUtils.isEmpty(recognition.title)
                      ? String.format("%s %.2f", recognition.title, recognition.detectionConfidence)
                      : String.format("%.2f", recognition.detectionConfidence);
      borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, labelString);
    }
  }

  private boolean initialized = false;

  public synchronized void onFrame(
          final int w,
          final int h,
          final int rowStride,
          final int sensorOrientation,
          final byte[] frame,
          final long timestamp) {
    if (objectTracker == null && !initialized) {
      ObjectTracker.clearInstance();

      logger.i("Initializing ObjectTracker: %dx%d", w, h);
      objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);
      frameWidth = w;
      frameHeight = h;
      this.sensorOrientation = sensorOrientation;
      initialized = true;

      if (objectTracker == null) {
        String message =
                "Object tracking support not found. "
                        + "See tensorflow/examples/android/README.md for details.";
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        logger.e(message);
      }
    }

    if (objectTracker == null) {
      return;
    }

    objectTracker.nextFrame(frame, null, timestamp, null, true);

    // Clean up any objects not worth tracking any more.
    final LinkedList<TrackedRecognition> copyList =
            new LinkedList<TrackedRecognition>(trackedObjects);
    for (final TrackedRecognition recognition : copyList) {
      final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
      final float correlation = trackedObject.getCurrentCorrelation();
      if (correlation < MIN_CORRELATION) {
        logger.v("Removing tracked object %s because NCC is %.2f", trackedObject, correlation);
        trackedObject.stopTracking();
        trackedObjects.remove(recognition);
      }
    }
  }

  private void processResults(
          final long timestamp, final List<Recognition> results, final byte[] originalFrame) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      logger.v("Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
    }

    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    if (objectTracker == null) {
      trackedObjects.clear();
      for (final Pair<Float, Recognition> potential : rectsToTrack) {
        final TrackedRecognition trackedRecognition = new TrackedRecognition();
        trackedRecognition.detectionConfidence = potential.first;
        trackedRecognition.location = new RectF(potential.second.getLocation());
        trackedRecognition.trackedObject = null;
        trackedRecognition.title = potential.second.getTitle();
        trackedRecognition.color = COLOR;
        trackedObjects.add(trackedRecognition);
      }
      return;
    }

    logger.i("%d rects to track", rectsToTrack.size());
    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      handleDetection(originalFrame, timestamp, potential);
    }
  }

  private void handleDetection(
          final byte[] frameCopy, final long timestamp, final Pair<Float, Recognition> potential) {
    final ObjectTracker.TrackedObject potentialObject =
            objectTracker.trackObject(potential.second.getLocation(), timestamp, frameCopy);

    final float potentialCorrelation = potentialObject.getCurrentCorrelation();
    logger.v(
            "Tracked object went from %s to %s with correlation %.2f",
            potential.second, potentialObject.getTrackedPositionInPreviewFrame(), potentialCorrelation);

    if (potentialCorrelation < MARGINAL_CORRELATION) {
      logger.v("Correlation too low to begin tracking %s.", potentialObject);
      potentialObject.stopTracking();
      return;
    }

    final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

    float maxIntersect = 0.0f;

    // Look for intersections that will be overridden by this object or an intersection that would
    // prevent this one from being placed.
    for (final TrackedRecognition trackedRecognition : trackedObjects) {
      final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
      final RectF b = potentialObject.getTrackedPositionInPreviewFrame();
      final RectF intersection = new RectF();
      final boolean intersects = intersection.setIntersect(a, b);

      final float intersectArea = intersection.width() * intersection.height();
      final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
      final float intersectOverUnion = intersectArea / totalArea;

      // If there is an intersection with this currently tracked box above the maximum overlap
      // percentage allowed, either the new recognition needs to be dismissed or the old
      // recognition needs to be removed and possibly replaced with the new one.
      if (intersects && intersectOverUnion > MAX_OVERLAP) {
        if (potential.first < trackedRecognition.detectionConfidence
                && trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
          // If track for the existing object is still going strong and the detection score was
          // good, reject this new object.
          potentialObject.stopTracking();
          return;
        } else {
          removeList.add(trackedRecognition);

          // Let the previously tracked object with max intersection amount donate its color to
          // the new object.
          if (intersectOverUnion > maxIntersect) {
            maxIntersect = intersectOverUnion;
          }
        }
      }
    }

    // Remove everything that got intersected.
    for (final TrackedRecognition trackedRecognition : removeList) {
      logger.v(
              "Removing tracked object %s with detection confidence %.2f, correlation %.2f",
              trackedRecognition.trackedObject,
              trackedRecognition.detectionConfidence,
              trackedRecognition.trackedObject.getCurrentCorrelation());
      trackedRecognition.trackedObject.stopTracking();
      trackedObjects.remove(trackedRecognition);
    }

    // Finally safe to say we can track this object.
    logger.v("Tracking object %s (%s) with detection confidence %.2f at position %s",
            potentialObject,
            potential.second.getTitle(),
            potential.first,
            potential.second.getLocation());
    final TrackedRecognition trackedRecognition = new TrackedRecognition();
    trackedRecognition.detectionConfidence = potential.first;
    trackedRecognition.trackedObject = potentialObject;
    trackedRecognition.title = potential.second.getTitle();

    // Use the color from a replaced object before taking one from the color queue.
    trackedRecognition.color = COLOR;
    trackedObjects.add(trackedRecognition);
  }
}
