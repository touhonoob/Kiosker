package dk.itu.kiosker.controllers;

import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import dk.itu.kiosker.activities.KioskerActivity;
import dk.itu.kiosker.models.Constants;
import dk.itu.kiosker.models.LocalSettings;
import dk.itu.kiosker.utils.CustomerErrorLogger;
import dk.itu.kiosker.utils.SettingsExtractor;
import dk.itu.kiosker.utils.WifiController;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

public class SettingsController {
    // We wait 5 seconds before starting scheduled tasks after a touch event.
    private final Observable<Long> delayedScheduledTasksObservable = Observable.timer(5, TimeUnit.SECONDS).subscribeOn(AndroidSchedulers.mainThread());
    private final KioskerActivity kioskerActivity;
    private final SoundController soundController;
    private final WebController webController;
    private final StandbyController standbyController;
    private final HardwareController hardwareController;
    private final WifiController wifiController;

    private final RefreshController refreshController;
    // List of the scheduled settings
    private final ArrayList<Subscriber> subscribers;
    private Subscriber<Long> delayedScheduledTasksSubscription;

    public SettingsController(KioskerActivity kioskerActivity) {
        this.kioskerActivity = kioskerActivity;
        subscribers = new ArrayList<>();
        soundController = new SoundController(kioskerActivity, subscribers, kioskerActivity);
        webController = new WebController(kioskerActivity, subscribers);
        standbyController = new StandbyController(kioskerActivity, subscribers);
        hardwareController = new HardwareController(kioskerActivity);
        refreshController = new RefreshController(kioskerActivity);
        wifiController = new WifiController(kioskerActivity);
    }

    public void handleSettings(LinkedHashMap settings, boolean baseSettings) {
        Constants.setString(kioskerActivity, SettingsExtractor.getString(settings, "passwordHash"), Constants.KIOSKER_PASSWORD_HASH_ID);
        Constants.setString(kioskerActivity, SettingsExtractor.getString(settings, "masterPasswordHash"), Constants.KIOSKER_MASTER_PASSWORD_HASH_ID);
        Constants.setString(kioskerActivity, SettingsExtractor.getString(settings, "passwordSalt"), Constants.KIOSKER_PASSWORD_SALT_ID);
        Constants.setString(kioskerActivity, SettingsExtractor.getString(settings, "masterPasswordSalt"), Constants.KIOSKER_MASTER_PASSWORD_SALT_ID);

        soundController.handleSoundSettings(settings);
        webController.handleWebSettings(settings);
        standbyController.handleStandbySettings(settings);
        hardwareController.handleHardwareSettings(settings);
        wifiController.handleWifiSettings(settings);

        // Save these settings as the safe defaults.
        if (!settings.isEmpty())
            LocalSettings.setSafeJson(kioskerActivity, settings);

        // Show our settings in the settings activity.
        Constants.settingsText = settingsToString(settings);

        // If the settings are empty we have failed to get any settings.
        if (settings.isEmpty() && baseSettings) {
            kioskerActivity.updateMainStatus(":(");
            kioskerActivity.updateSubStatus("Sorry - error while downloading. Wrong base url?");
            kioskerActivity.createSecretMenuButton();
        } else
            kioskerActivity.removeStatusTextViews();

        // When all the settings have been parsed check to see if we should hide the ui.
        HardwareController.handleNavigationUI();

        // Set the user provided brightness
        StandbyController.unDimDevice(kioskerActivity);
    }

    /**
     * String extractor for a map
     * @param mp
     */
    public static String settingsToString(Map mp) {
        String result = "";
        Iterator it = mp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            result += pairs.getKey() + " = " + pairs.getValue() + "\n";
            it.remove();
        }
        return result;
    }

    /**
     * Load and handle safe settings from local storage.
     * These settings are stored every time the app
     * successfully downloads and parses the online settings.
     */
    public void loadSafeSettings() {
        Log.d(Constants.TAG, "Loading safe settings.");
        handleSettings(LocalSettings.getSafeJson(kioskerActivity), true);
    }

    private Subscriber<Long> getDelayedScheduledTasksSubscription() {
        delayedScheduledTasksSubscription = new Subscriber<Long>() {
            @Override
            public void onCompleted() {
                subscribers.remove(delayedScheduledTasksSubscription);
            }

            @Override
            public void onError(Throwable e) {
                CustomerErrorLogger.log("Error while starting delayed tasks.", e, kioskerActivity);
            }

            @Override
            public void onNext(Long aLong) {
                Log.d(Constants.TAG, "Restarting scheduled tasks.");
                kioskerActivity.userIsInteractingWithDevice = false;
                webController.startScreenSaverSubscription();
                webController.startCycleSecondarySubscription();
                webController.startResetToHomeSubscription();
                standbyController.startDimSubscription();
                if (refreshController.deviceShouldBeReset)
                    refreshController.startShortRefreshSubscription();
            }
        };
        return delayedScheduledTasksSubscription;
    }

    public void startScheduledTasks() {
        if (delayedScheduledTasksSubscription != null && delayedScheduledTasksSubscription.isUnsubscribed())
            delayedScheduledTasksObservable.subscribe(getDelayedScheduledTasksSubscription());
        else {
            if (delayedScheduledTasksSubscription != null)
                delayedScheduledTasksSubscription.unsubscribe();
            delayedScheduledTasksObservable.subscribe(getDelayedScheduledTasksSubscription());
        }
    }

    public void stopScheduledTasks() {
        webController.stopScreenSaverSubscription();
        webController.stopCycleSecondarySubscription();
        webController.stopResetToHomeSubscription();
        standbyController.stopDimSubscription();
        refreshController.stopShortRefreshSubscription();
    }

    public void unsubscribeScheduledTasks() {
        // If we are in standby mode make sure that we don't unsubscribe our wake subscriber.
        if (kioskerActivity.currentlyInStandbyPeriod)
            subscribers.remove(kioskerActivity.wakeSubscriber);
        for (Subscriber s : subscribers)
            s.unsubscribe();
        subscribers.clear();
    }

    public void clearWebViews() {
        webController.clearWebViews();
    }

    public void handleNavigationUI() {
        hardwareController.handleNavigationUI();
    }

    public void keepScreenOn() {
        standbyController.keepScreenOn();
    }

    public void handleOnResume() {
        standbyController.handleOnResume();
    }

    public void handleOnPause() {
        standbyController.handleOnPause();
    }
}