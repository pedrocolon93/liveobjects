package edu.mit.media.obm.liveobjects.apptidmarsh.module;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import com.noveogroup.android.log.Log;
import com.squareup.otto.Bus;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import edu.mit.media.obm.liveobjects.apptidmarsh.main.DiscoveryInfo;
import edu.mit.media.obm.liveobjects.apptidmarsh.main.DiscoveryOverseer;
import edu.mit.media.obm.liveobjects.apptidmarsh.main.DiscoveryRunner;
import edu.mit.media.obm.liveobjects.apptidmarsh.main.MainActivity;
import edu.mit.media.obm.liveobjects.apptidmarsh.media.MediaViewActivity;
import edu.mit.media.obm.liveobjects.apptidmarsh.notifications.AlarmReceiver;
import edu.mit.media.obm.liveobjects.apptidmarsh.notifications.DiscoveryService;
import edu.mit.media.obm.liveobjects.apptidmarsh.notifications.PeriodicAlarmManager;
import edu.mit.media.obm.liveobjects.apptidmarsh.utils.BluetoothNotifier;
import edu.mit.media.obm.liveobjects.apptidmarsh.utils.LiveObjectNotifier;
import edu.mit.media.obm.liveobjects.driver.wifi.WifiConnectionManager;
import edu.mit.media.obm.liveobjects.driver.wifi.WifiNetworkBus;
import edu.mit.media.obm.liveobjects.middleware.control.DbController;
import edu.mit.media.obm.liveobjects.middleware.control.NetworkController;

/**
 * Created by artimo14 on 8/1/15.
 */
@Module(
        library = true,
        complete = false,
        includes = SystemModule.class,
        injects = {
                BluetoothNotifier.class,
                DiscoveryService.class,
                PeriodicAlarmManager.class,
                MainActivity.class,
                MediaViewActivity.class
        }
)
public class ApplicationModule {
    private static Bus mBus = null;

    public ApplicationModule() {
    }

    @Provides
    LiveObjectNotifier provideLiveObjectNotifier(Context context) {
        return new BluetoothNotifier(context);
    }

    @Provides @Singleton Bus provideBus() {
        // @Singleton annotation guarantees that the returned object exists one-per-objectGraph,
        // not one-per-application
        if (mBus == null) {
            Log.v("create Bus");
            mBus = new Bus();
        }

        return mBus;
    }

    @Provides @Named("network_wifi") @Singleton Bus provideNetworkWifiBus() {
        return WifiNetworkBus.getBus();
    }

    @Provides
    Intent provideAlarmReceiverIntent(Context context) {
        return new Intent(context, AlarmReceiver.class);
    }

    @Provides
    PeriodicAlarmManager providePeriodicAlarmManager(Intent alarmReceiverIntent, Context context, AlarmManager alarmManager) {
        return new PeriodicAlarmManager(alarmReceiverIntent, context, alarmManager
        );
    }

    @Provides
    DiscoveryInfo provideDiscoveryInfo(DbController dbController) {
        return new DiscoveryInfo(dbController);
    }

    @Provides
    DiscoveryRunner provideDiscoveryRunner(NetworkController networkController, LiveObjectNotifier liveObjectNotifier,
                                           Bus bus, @Named("network_wifi") Bus networkConnectionBus) {
        return new DiscoveryRunner(networkController, liveObjectNotifier, bus, networkConnectionBus);
    }

    @Provides
    DiscoveryOverseer provideDiscoveryOverseerDiscoveryOverseer(
            DbController dbController, DiscoveryInfo discoveryInfo,
            DiscoveryRunner discoveryRunner, Bus bus, @Named("network_wifi") Bus networkWifiBus) {
        return new DiscoveryOverseer(dbController, discoveryInfo, discoveryRunner, bus, networkWifiBus);
    }
}
