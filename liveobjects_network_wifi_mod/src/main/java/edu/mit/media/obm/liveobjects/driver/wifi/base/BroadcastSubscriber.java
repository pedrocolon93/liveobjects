package edu.mit.media.obm.liveobjects.driver.wifi.base;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import javax.inject.Inject;
import javax.inject.Named;

import edu.mit.media.obm.liveobjects.driver.wifi.module.DependencyInjector;

/**
 * Created by arata on 9/11/15.
 */
public abstract class BroadcastSubscriber extends ActivatableEntity {
    @Inject @Named("application") protected Context context;

    protected BroadcastReceiver broadcastReceiver;

    @Override
    protected void activateEntity() {
        broadcastReceiver = createBroadcastReceiver();
        IntentFilter intentFilter = createIntentFilter();

        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void deactivateEntity() {
        context.unregisterReceiver(broadcastReceiver);
    }

    protected abstract BroadcastReceiver createBroadcastReceiver();
    protected abstract IntentFilter createIntentFilter();
}