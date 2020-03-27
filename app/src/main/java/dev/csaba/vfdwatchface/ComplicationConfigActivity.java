package dev.csaba.vfdwatchface;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.Nullable;

import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationHelperActivity;
import android.support.wearable.complications.ComplicationProviderInfo;
import android.support.wearable.complications.ProviderChooserIntent;
import android.support.wearable.complications.ProviderInfoRetriever;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * The watch-side config activity for {@link VFDWatchFace}, which allows for setting
 * the left and right complications of watch face.
 */
public class ComplicationConfigActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "ComplicationConfigActivity";

    static final int COMPLICATION_CONFIG_REQUEST_CODE = 1001;

    private static final int[] COMPLICATION_SUPPORTED_TYPES = {
        ComplicationData.TYPE_RANGED_VALUE,
        ComplicationData.TYPE_ICON,
        ComplicationData.TYPE_SHORT_TEXT,
        ComplicationData.TYPE_SMALL_IMAGE
    };

    private static final int MIN_LOCATION_INDEX = 0;
    private static final int MAX_LOCATION_INDEX = 5;
    public static final int[] LOCATION_INDEXES =
            IntStream.rangeClosed(MIN_LOCATION_INDEX, MAX_LOCATION_INDEX).toArray();
    private static final int[] BACKGROUND_RESOURCE_IDS = {
        R.id.top_left_complication_background,
        R.id.top_center_complication_background,
        R.id.top_right_complication_background,
        R.id.bottom_left_complication_background,
        R.id.bottom_center_complication_background,
        R.id.bottom_right_complication_background
    };
    private static final int[] COMPLICATION_RESOURCE_IDS = {
        R.id.top_left_complication,
        R.id.top_center_complication,
        R.id.top_right_complication,
        R.id.bottom_left_complication,
        R.id.bottom_center_complication,
        R.id.bottom_right_complication
    };

    // Selected complication id by user.
    private int selectedComplicationId;

    // ComponentName used to identify a specific service that renders the watch face.
    private ComponentName watchFaceComponentName;

    // Required to retrieve complication data from watch face for preview.
    private ProviderInfoRetriever providerInfoRetriever;

    private ImageView[] complicationBackgrounds;
    private ImageButton[] complications;

    private Drawable defaultAddComplicationDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_config);

        defaultAddComplicationDrawable = getDrawable(R.drawable.add_complication);

        selectedComplicationId = -1;

        watchFaceComponentName =
                new ComponentName(getApplicationContext(), VFDWatchFace.class);

        complicationBackgrounds = new ImageView[MAX_LOCATION_INDEX + 1];
        complications = new ImageButton[MAX_LOCATION_INDEX + 1];

        // Sets up left complication preview.
        for (int locationIndex : LOCATION_INDEXES) {
            complicationBackgrounds[locationIndex] =
                    findViewById(BACKGROUND_RESOURCE_IDS[locationIndex]);
            complications[locationIndex] =
                    findViewById(COMPLICATION_RESOURCE_IDS[locationIndex]);
            complications[locationIndex].setOnClickListener(this);

            // Sets default as "Add Complication" icon.
            complications[locationIndex].setImageDrawable(defaultAddComplicationDrawable);
            complicationBackgrounds[locationIndex].setVisibility(View.INVISIBLE);
        }

        // Initialization of code to retrieve active complication data for the watch face.
        providerInfoRetriever =
                new ProviderInfoRetriever(getApplicationContext(), Executors.newCachedThreadPool());
        providerInfoRetriever.init();

        retrieveInitialComplicationsData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Required to release retriever for active complication data.
        providerInfoRetriever.release();
    }

    public void retrieveInitialComplicationsData() {
        providerInfoRetriever.retrieveProviderInfo(
                new ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    @Override
                    public void onProviderInfoReceived(
                            int watchFaceComplicationId,
                            @Nullable ComplicationProviderInfo complicationProviderInfo) {

                        Log.d(TAG, "onProviderInfoReceived: " + complicationProviderInfo);

                        updateComplicationViews(watchFaceComplicationId, complicationProviderInfo);
                    }
                },
                watchFaceComponentName,
                LOCATION_INDEXES);
    }

    @Override
    public void onClick(View view) {
        int complicationIndex = 0;
        for (ImageButton complication: complications) {
            if (view.equals(complication)) {
                Log.d(TAG, String.format("Complication %d click()", complicationIndex));
                launchComplicationHelperActivity(complicationIndex);
            }
        }
    }

    // Verifies the watch face supports the complication location, then launches the helper
    // class, so user can choose their complication data provider.
    private void launchComplicationHelperActivity(int complicationIndex) {

        selectedComplicationId = complicationIndex;

        if (selectedComplicationId >= MIN_LOCATION_INDEX && selectedComplicationId <= MAX_LOCATION_INDEX) {
            Log.d(TAG, "launchComplicationHelperActivity for " + selectedComplicationId);

            startActivityForResult(
                    ComplicationHelperActivity.createProviderChooserHelperIntent(
                            getApplicationContext(),
                            watchFaceComponentName,
                            selectedComplicationId,
                            COMPLICATION_SUPPORTED_TYPES),
                    ComplicationConfigActivity.COMPLICATION_CONFIG_REQUEST_CODE);

        } else {
            Log.d(TAG, "Complication not supported by watch face.");
        }
    }

    public void updateComplicationViews(
            int watchFaceComplicationId, ComplicationProviderInfo complicationProviderInfo) {
        Log.d(TAG, "updateComplicationViews(): id: " + watchFaceComplicationId);
        Log.d(TAG, "\tinfo: " + complicationProviderInfo);

        if (watchFaceComplicationId >= MIN_LOCATION_INDEX && watchFaceComplicationId <= MAX_LOCATION_INDEX) {
            if (complicationProviderInfo != null) {
                complications[watchFaceComplicationId].setImageIcon(complicationProviderInfo.providerIcon);
                complicationBackgrounds[watchFaceComplicationId].setVisibility(View.VISIBLE);
            } else {
                complications[watchFaceComplicationId].setImageDrawable(defaultAddComplicationDrawable);
                complicationBackgrounds[watchFaceComplicationId].setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == COMPLICATION_CONFIG_REQUEST_CODE && resultCode == RESULT_OK) {
            // Retrieves information for selected Complication provider.
            ComplicationProviderInfo complicationProviderInfo =
                    data.getParcelableExtra(ProviderChooserIntent.EXTRA_PROVIDER_INFO);
            Log.d(TAG, "Provider: " + complicationProviderInfo);

            if (selectedComplicationId >= 0) {
                updateComplicationViews(selectedComplicationId, complicationProviderInfo);
            }
        }
    }
}
