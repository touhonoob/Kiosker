package dk.itu.kiosker.models;

import android.util.Log;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import dk.itu.kiosker.activities.InitialSetup;
import dk.itu.kiosker.activities.KioskerActivity;
import dk.itu.kiosker.utils.CustomerErrorLogger;
import dk.itu.kiosker.utils.JsonFetcher;
import retrofit.RetrofitError;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

public class OnlineSettings {
    private static LinkedHashMap currentSettings;

    public static void getSettings(KioskerActivity kioskerActivity) {
        Constants.JSON_BASE_URL = Constants.getString(kioskerActivity, Constants.KIOSKER_JSON_BASE_URL_ID);

        if (!Constants.JSON_BASE_URL.isEmpty()) {
            JsonFetcher fetcher = new JsonFetcher();
            fetcher.getObservableMap(Constants.BASE_SETTINGS + Constants.FILE_ENDING)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(baseSettingsObserver(kioskerActivity));
        } else {
            LinkedHashMap emptyMap = new LinkedHashMap();
            Observable.from(emptyMap)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(baseSettingsObserver(kioskerActivity));
        }
    }

    // Observer we use to consume the base json settings.
    private static Observer<LinkedHashMap> baseSettingsObserver(final KioskerActivity kioskerActivity) {
        return new Observer<LinkedHashMap>() {
            @Override
            public void onCompleted() {
                Log.d(Constants.TAG, "Finished getting base json settings.");
                kioskerActivity.updateSubStatus("Finished downloading base settings.");
                String device_id = Constants.getString(kioskerActivity, Constants.KIOSKER_DEVICE_ID);
                if (!device_id.isEmpty()) {
                    JsonFetcher jsonFetcher = new JsonFetcher();
                    jsonFetcher.getObservableMap(device_id + Constants.FILE_ENDING)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(deviceSpecificSettingsObserver(kioskerActivity));
                } else
                    kioskerActivity.handleSettings(currentSettings, true);
            }

            @Override
            public void onError(Throwable throwable) {
                RetrofitError error = null;
                String errorReason = "Unknown parse error.";
                if (throwable != null && throwable.getClass().equals(RetrofitError.class)) {
                    error = (RetrofitError) throwable;
                    if (error.getResponse() != null && error.getResponse().getReason() != null)
                        errorReason = error.getResponse().getReason();
                }

                if (error != null) {
                    CustomerErrorLogger.log("Error while getting base json settings because " + errorReason + ".", error, kioskerActivity);
                } else {
                    CustomerErrorLogger.log("Error while getting base json settings because " + errorReason + ".", throwable, kioskerActivity);

                }


                kioskerActivity.updateMainStatus("Error");
                if (Constants.hasSafeSettings(kioskerActivity)) {
                    kioskerActivity.updateSubStatus(errorReason + ", trying safe settings.");

                    // Of there was an error getting the json we can load or last successful json
                    Observable.timer(3, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                        @Override
                        public void call(Long aLong) {
                            kioskerActivity.cleanUpMainView();
                            kioskerActivity.loadSafeSettings();
                        }
                    });
                } else {
                    kioskerActivity.updateSubStatus(errorReason + ", please retry.");
                    Observable.timer(3, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<Long>() {
                        @Override
                        public void call(Long aLong) {
                            InitialSetup.start(kioskerActivity);
                        }
                    });
                }
            }

            @Override
            public void onNext(LinkedHashMap settings) {
                // Set the current settings to be the downloaded base settings.
                currentSettings = settings;
            }
        };
    }

    // Observer we use to consume the device specific json settings.
    private static Observer<LinkedHashMap> deviceSpecificSettingsObserver(final KioskerActivity kioskerActivity) {
        return new Observer<LinkedHashMap>() {
            @Override
            public void onCompleted() {
                Log.d(Constants.TAG, "Finished getting device specific json settings.");
                kioskerActivity.updateSubStatus("Finished downloading device specific settings.");
                kioskerActivity.handleSettings(currentSettings, false);
            }

            @Override
            public void onError(Throwable throwable) {
                if (throwable != null && throwable.getClass().equals(RetrofitError.class)) {
                    RetrofitError error = (RetrofitError) throwable;
                    String errorReason = error.getResponse().getReason();
                    Log.e(Constants.TAG, "Error while getting device specific json settings because " + errorReason + ".", throwable);
                    Toast.makeText(kioskerActivity, "Error getting device specific json settings: " + errorReason, Toast.LENGTH_LONG).show();
                    kioskerActivity.handleSettings(currentSettings, false);
                }
            }

            @Override
            public void onNext(LinkedHashMap settings) {
                // Combine the base settings with the user specific settings.
                currentSettings.putAll(settings);
            }
        };
    }
}
