package dev.csaba.vfdwatchface;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import android.preference.PreferenceManager;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class VFDWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "VFDWatchFace";

    /*
     * Update rate in milliseconds for interactive mode. Updating once a second to advance seconds.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final String TIME_COLOR_TAG = "time";
    private static final String DIVIDER_COLOR_TAG = "divider";
    private static final Map<String, Map<String, Integer>> COLOR_MAP =
            new HashMap<String, Map<String, Integer>>()
    {{
        put("r", new HashMap<String, Integer>() {{
            put(TIME_COLOR_TAG, Color.YELLOW);
            put(DIVIDER_COLOR_TAG, Color.parseColor("#FFBF00"));  // Amber
        }});
        put("g", new HashMap<String, Integer>() {{
            put(TIME_COLOR_TAG, Color.GREEN);
            put(DIVIDER_COLOR_TAG, Color.parseColor("#00FFBF"));
        }});
        put("b", new HashMap<String, Integer>() {{
            put(TIME_COLOR_TAG, Color.CYAN);
            put(DIVIDER_COLOR_TAG, Color.parseColor("#00BFFF"));
        }});
    }};
    private static final Map<String, Integer> COMPLICATION_COLOR_MAP =
        new HashMap<String, Integer>() {{
            put("r", R.drawable.red_complication_styles);
            put("g", R.drawable.green_complication_styles);
            put("b", R.drawable.blue_complication_styles);
        }};

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<VFDWatchFace.Engine> mWeakReference;

        EngineHandler(VFDWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            VFDWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                if (msg.what == MSG_UPDATE_TIME) {
                    engine.handleUpdateTimeMessage();
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        /* Handler to update the time once a second in interactive mode. */
        private final Handler updateTimeHandler = new EngineHandler(this);
        private Calendar calendar;
        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean registeredTimeZoneReceiver = false;
        private boolean muteMode;

        private SimpleDateFormat normalTimeFormat;
        private SimpleDateFormat ambientTimeFormat;

        private TextPaint timePaint;
        private TextPaint dividerPaint;
        private Typeface vt323Typeface;
        private DisplayMetrics displayMetrics;
        private int fontSize = 28;
        private volatile String complicationColorScheme;
        private volatile String paintColorScheme;

        private boolean ambient;

        /* Maps active complication ids to the data for that complication. Note: Data will only be
         * present if the user has chosen a provider via the settings activity for the watch face.
         */
        private SparseArray<ComplicationData> activeComplicationDataSparseArray;

        /* Maps complication ids to corresponding ComplicationDrawable that renders the
         * the complication data on the watch face.
         */
        private SparseArray<ComplicationDrawable> complicationDrawableSparseArray;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(VFDWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());

            calendar = Calendar.getInstance();

            vt323Typeface = getResources().getFont(R.font.vt323_font);
            displayMetrics = getResources().getDisplayMetrics();
            fontSize = (int)TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 28, displayMetrics);
            String colorScheme = getColorScheme();

            initializeComplications(colorScheme);

            initializeWatchFace(colorScheme);
        }

        private SharedPreferences getPreferences() {
            return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        }

        private String getColorScheme() {
            return getPreferences().getString(ComplicationConfigActivity.COLOR_SCHEME_TAG, "r");
        }

        private void setComplicationDrawable(int complicationId, String colorScheme, Context appContext) {
            ComplicationDrawable complicationDrawable =
                    (ComplicationDrawable) getDrawable(COMPLICATION_COLOR_MAP.get(colorScheme));

            if (complicationDrawable != null) {
                complicationDrawable.setTextTypefaceActive(vt323Typeface);
                complicationDrawable.setTextTypefaceAmbient(vt323Typeface);
                complicationDrawable.setTextSizeActive(fontSize);
                complicationDrawable.setTextSizeAmbient(fontSize);
                complicationDrawable.setTitleTypefaceActive(vt323Typeface);
                complicationDrawable.setTitleTypefaceAmbient(vt323Typeface);
                complicationDrawable.setTitleSizeActive(fontSize);
                complicationDrawable.setTitleSizeAmbient(fontSize);
                complicationDrawable.setContext(appContext);
            }

            // Adds new complications to a SparseArray to simplify setting styles and ambient
            // properties for all complications, i.e., iterate over them all.
            complicationDrawableSparseArray.put(complicationId, complicationDrawable);
        }

        private void initializeComplications(String colorScheme)
        {
            Log.d(TAG, "initializeComplications()");

            activeComplicationDataSparseArray =
                    new SparseArray<>(ComplicationConfigActivity.LOCATION_INDEXES.length);
            complicationDrawableSparseArray =
                    new SparseArray<>(ComplicationConfigActivity.LOCATION_INDEXES.length);

            // Creates a ComplicationDrawable for each location where the user can render a
            // complication on the watch face. In this watch face, we only create left and right,
            // but you could add many more.
            // All styles for the complications are defined in
            // drawable/*_complication_styles.xml.
            Context appContext = getApplicationContext();
            for (int complicationId : ComplicationConfigActivity.LOCATION_INDEXES) {
                setComplicationDrawable(complicationId, colorScheme, appContext);
            }
            complicationColorScheme = colorScheme;

            setActiveComplications(ComplicationConfigActivity.LOCATION_INDEXES);
        }

        private void initializeWatchFace(String colorScheme)
        {
            /* Set defaults for colors */
            // We setup the time formatter
            normalTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            ambientTimeFormat = new SimpleDateFormat("hh:mma", Locale.getDefault());

            paintColorScheme = colorScheme;
            // The time paint
            timePaint = new TextPaint();
            timePaint.setColor(COLOR_MAP.get(colorScheme).get(TIME_COLOR_TAG));
            timePaint.setAntiAlias(true);
            timePaint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 68, displayMetrics));
            timePaint.setTypeface(vt323Typeface);

            // Divider paint
            dividerPaint = new TextPaint();
            dividerPaint.setColor(COLOR_MAP.get(colorScheme).get(DIVIDER_COLOR_TAG));
            dividerPaint.setAntiAlias(true);
            dividerPaint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 68, displayMetrics));
            dividerPaint.setTypeface(vt323Typeface);
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            /*
             * Whether the display supports fewer bits for each color in ambient mode.
             * When true, we disable anti-aliasing in ambient mode.
             */
            boolean lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            /*
             * Whether the display supports burn in protection in ambient mode.
             * When true, remove the background in ambient mode.
             */
            boolean mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            Log.d(TAG, "onPropertiesChanged");
            // Updates complications to properly render in ambient mode based on the
            // screen's capabilities.
            ComplicationDrawable complicationDrawable;

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                complicationDrawable = complicationDrawableSparseArray.get(complicationIndex);
                String colorScheme = getColorScheme();
                if (complicationColorScheme != null && !colorScheme.equals(complicationColorScheme)) {
                    complicationColorScheme = colorScheme;
                    Log.d(TAG, "Re init ComplicationDrawable color schemes");
                    Context appContext = getApplicationContext();
                    for (int complicationId : ComplicationConfigActivity.LOCATION_INDEXES) {
                        setComplicationDrawable(complicationId, colorScheme, appContext);
                    }
                }

                if (complicationDrawable != null) {
                    complicationDrawable.setLowBitAmbient(lowBitAmbient);
                    complicationDrawable.setBurnInProtection(mBurnInProtection);
                }
            }
        }

        @Override
        public void onComplicationDataUpdate(
                int complicationId, ComplicationData complicationData) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId);

            // Adds/updates active complication data in the array.
            activeComplicationDataSparseArray.put(complicationId, complicationData);

            // Updates correct ComplicationDrawable with updated data.
            ComplicationDrawable complicationDrawable =
                    complicationDrawableSparseArray.get(complicationId);
            complicationDrawable.setComplicationData(complicationData);

            invalidate();
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Log.d(TAG, String.format("OnTapCommand(%d, %d, %d)", tapType, x, y));
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    int tappedComplicationIndex = getTappedComplicationIndex(x, y);
                    if (tappedComplicationIndex != -1) {
                        onComplicationTap(tappedComplicationIndex);
                    }
                    break;
            }
            if (tapType != TAP_TYPE_TAP) {
                invalidate();
            }
        }

        /*
         * Determines if tap inside a complication area or returns -1.
         */
        private int getTappedComplicationIndex(int x, int y) {

            long currentTimeMillis = System.currentTimeMillis();

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationData complicationData =
                        activeComplicationDataSparseArray.get(complicationIndex);

                if (complicationData != null
                    && complicationData.isActive(currentTimeMillis)
                    && complicationData.getType() != ComplicationData.TYPE_NOT_CONFIGURED
                    && complicationData.getType() != ComplicationData.TYPE_EMPTY)
                {

                    ComplicationDrawable complicationDrawable =
                            complicationDrawableSparseArray.get(complicationIndex);
                    Rect complicationBoundingRect = complicationDrawable.getBounds();

                    if (complicationBoundingRect.width() > 0) {
                        if (complicationBoundingRect.contains(x, y)) {
                            return complicationIndex;
                        }
                    } else {
                        Log.e(TAG, "Not a recognized complication id.");
                    }
                }
            }
            return -1;
        }

        // Fires PendingIntent associated with complication (if it has one).
        private void onComplicationTap(int complicationIndex) {
            Log.d(TAG, "onComplicationTap()");

            ComplicationData complicationData =
                    activeComplicationDataSparseArray.get(complicationIndex);

            if (complicationData != null) {

                if (complicationData.getTapAction() != null) {
                    try {
                        complicationData.getTapAction().send();
                    } catch (PendingIntent.CanceledException e) {
                        Log.e(TAG, "onComplicationTap() tap action error: " + e);
                    }

                } else if (complicationData.getType() == ComplicationData.TYPE_NO_PERMISSION) {

                    // Watch face does not have permission to receive complication data, so launch
                    // permission request.
                    ComponentName componentName =
                            new ComponentName(
                                    getApplicationContext(), CanvasWatchFaceService.class);

                    Intent permissionRequestIntent =
                            ComplicationHelperActivity.createPermissionRequestHelperIntent(
                                    getApplicationContext(), componentName);

                    startActivity(permissionRequestIntent);
                }

            } else {
                Log.d(TAG, "No PendingIntent for complication " + complicationIndex + ".");
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            ambient = inAmbientMode;

            updateWatchHandStyle();

            // Update drawable complications' ambient state.
            // Note: ComplicationDrawable handles switching between active/ambient colors, we just
            // have to inform it to enter ambient mode.

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationDrawable complicationDrawable =
                    complicationDrawableSparseArray.get(complicationIndex);
                complicationDrawable.setInAmbientMode(ambient);
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            boolean antiAlias = !ambient;
            timePaint.setAntiAlias(antiAlias);
            dividerPaint.setAntiAlias(antiAlias);
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;

            /* Dim display in mute mode. */
            if (muteMode != inMuteMode) {
                muteMode = inMuteMode;
                int alpha = inMuteMode ? 100 : 255;
                timePaint.setAlpha(alpha);
                dividerPaint.setAlpha(alpha);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Calculates location bounds for top and bottom circular complication rows.
             * Please note, no long text complications in this watch face.
             *
             * We suggest using at least 1/4 of the screen width for circular (or squared)
             * complications and 2/3 of the screen width for wide rectangular complications for
             * better readability.
             */

            // For most Wear devices, width and height are the same, so we just chose one (width).
            int sizeOfComplication = width / 4;
            int midpointOfScreen = width / 2;
            int gap = width / 64;

            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                int verticalOffset;
                if (complicationIndex < 3) {
                    verticalOffset = midpointOfScreen - sizeOfComplication * 3 / 2 + gap;
                } else {
                    verticalOffset = midpointOfScreen + sizeOfComplication / 2 - gap;
                }
                int horizontalOffset = 0;
                switch (complicationIndex % 3) {
                    case 0:
                        horizontalOffset = midpointOfScreen - sizeOfComplication * 3 / 2 - gap;
                        break;
                    case 1:
                        horizontalOffset = midpointOfScreen - sizeOfComplication / 2;
                        break;
                    case 2:
                        horizontalOffset = midpointOfScreen + sizeOfComplication / 2 + gap;
                        break;
                }

                Rect complicationBounds =
                        // Left, Top, Right, Bottom
                        new Rect(
                                horizontalOffset,
                                verticalOffset,
                                (horizontalOffset + sizeOfComplication),
                                (verticalOffset + sizeOfComplication));

                Log.d(TAG, String.format("Complication %d bounds, %d x %d %d x %d",
                        complicationIndex, complicationBounds.left, complicationBounds.top,
                        complicationBounds.width(), complicationBounds.height()));

                ComplicationDrawable complicationDrawable =
                        complicationDrawableSparseArray.get(complicationIndex);
                complicationDrawable.setBounds(complicationBounds);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);

            long now = System.currentTimeMillis();
            calendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawComplications(canvas, now);
            drawWatchFace(canvas, bounds);
        }

        private void drawBackground(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
        }

        private void drawComplications(Canvas canvas, long currentTimeMillis) {
            for (int complicationIndex : ComplicationConfigActivity.LOCATION_INDEXES) {
                ComplicationDrawable complicationDrawable =
                        complicationDrawableSparseArray.get(complicationIndex);

                complicationDrawable.draw(canvas, currentTimeMillis);
            }
        }

        private void drawWatchFace(Canvas canvas, Rect bounds) {

            if (ambient) {
                Rect timeBounds = new Rect();
                String timeText = ambientTimeFormat.format(calendar.getTime());
                int timeX;
                int timeY;

                timePaint.getTextBounds(timeText, 0, timeText.length(), timeBounds);
                timeX = Math.abs(bounds.centerX() - timeBounds.centerX());
                timeY = Math.abs(bounds.centerY() - timeBounds.centerY());

                canvas.drawText(timeText, timeX, timeY, timePaint);
            } else {
                Rect timeBounds = new Rect();
                String timeText = normalTimeFormat.format(calendar.getTime());
                String[] timeParts = timeText.split(":");

                int margin = bounds.width() / 12;
                boolean paintDividers = calendar.get(Calendar.SECOND) % 2 == 0;

                // Minute: in center
                timePaint.getTextBounds(timeParts[1], 0, timeParts[1].length(), timeBounds);
                int minuteX = Math.abs(bounds.centerX() - timeBounds.centerX());
                int minuteY = Math.abs(bounds.centerY() - timeBounds.centerY());
                int minuteRight = minuteX + timeBounds.width();

                canvas.drawText(timeParts[1], minuteX, minuteY, timePaint);

                // Hour
                timePaint.getTextBounds(timeParts[0], 0, timeParts[0].length(), timeBounds);
                int hourX = minuteX - timeBounds.width() - margin;

                canvas.drawText(timeParts[0], hourX, minuteY, timePaint);

                if (paintDividers) {
                    canvas.drawText(":", minuteX - margin, minuteY, dividerPaint);
                }

                // Second
                timePaint.getTextBounds(timeParts[2], 0, timeParts[2].length(), timeBounds);
                int secondX = minuteRight + margin;

                canvas.drawText(timeParts[2], secondX, minuteY, timePaint);

                if (paintDividers) {
                    canvas.drawText(":", secondX - margin, minuteY, dividerPaint);
                }
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            Log.d(TAG, "onVisibilityChanged");
            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                calendar.setTimeZone(TimeZone.getDefault());
                String colorScheme = getColorScheme();
                if (paintColorScheme != null && !paintColorScheme.equals(colorScheme)) {
                    paintColorScheme = colorScheme;
                    timePaint.setColor(COLOR_MAP.get(colorScheme).get(TIME_COLOR_TAG));
                    dividerPaint.setColor(COLOR_MAP.get(colorScheme).get(DIVIDER_COLOR_TAG));
                    Log.d(TAG, "Re init paint color schemes");
                }
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            VFDWatchFace.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            VFDWatchFace.this.unregisterReceiver(timeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #updateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !ambient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
