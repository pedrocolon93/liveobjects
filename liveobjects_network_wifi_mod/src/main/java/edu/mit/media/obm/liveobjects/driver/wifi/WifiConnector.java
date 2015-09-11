package edu.mit.media.obm.liveobjects.driver.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;

import com.noveogroup.android.log.Log;

import java.util.List;

import edu.mit.media.obm.liveobjects.middleware.common.LiveObject;
import edu.mit.media.obm.liveobjects.middleware.net.DeviceIdTranslator;
import edu.mit.media.obm.liveobjects.middleware.net.NetworkListener;

/**
 * Created by arata on 9/11/15.
 */
public class WifiConnector {
    private final String NETWORK_PASSWORD;

    private NetworkListener mNetworkListener;

    private WifiManager mWifiManager;

    private WifiReceiver mWifiReceiver;

    private Context mContext;

    private IntentFilter mIntentFilter;

    private boolean mConnecting;
    private int mConnectingNetworkId;

    private DeviceIdTranslator mDeviceIdTranslator;

    public WifiConnector(Context context, DeviceIdTranslator deviceIdTranslator) {
        mContext = context;
        mDeviceIdTranslator = deviceIdTranslator;

        Resources resources = mContext.getResources();
        NETWORK_PASSWORD = resources.getString(R.string.network_password);
    }

    public void initialize() {
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mWifiReceiver = new WifiReceiver();

        mConnecting = false;
    }

    protected void start() {
        mContext.registerReceiver(mWifiReceiver, mIntentFilter);
    }

    protected void stop() {
        mContext.unregisterReceiver(mWifiReceiver);
    }

    synchronized public void connect(LiveObject liveObject) throws IllegalStateException {
        if (isConnecting()) {
            throw new IllegalStateException("Must not try to connect when it's already connecting");
        }

        mConnecting = true;

        String deviceId = mDeviceIdTranslator.translateFromLiveObject(liveObject);

        // executes as an asynchronous task because WifiManager.getConfiguredNetwork() may block.
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                String deviceId = params[0];

                WifiConfiguration config = WifiManagerWrapper.addNewNetwork(mWifiManager, deviceId, NETWORK_PASSWORD);
                WifiManagerWrapper.connectToConfiguredNetwork(mContext, mWifiManager, config, true);

                mConnectingNetworkId = config.networkId;

                mWifiManager.enableNetwork(mConnectingNetworkId, true);
                return null;
            }
        }.execute(deviceId);
    }

    synchronized public void cancelConnecting() throws IllegalStateException{
        if (!isConnecting()) {
            throw new IllegalStateException("Must not try to cancel when it's not connecting");
        }

        mWifiManager.disableNetwork(mConnectingNetworkId);
        mConnecting = false;
    }

    public boolean isConnecting() {
        return mConnecting;
    }

    public void setNetworkListener(NetworkListener networkListener) {
        mNetworkListener = networkListener;
    }

    synchronized public void forgetNetworkConfigurations() throws IllegalStateException {
        if (isConnecting()) {
            throw new IllegalStateException("Must not try to disconnect when it's already connecting");
        }

        // executes as an asynchronous task because WifiManager.getConfiguredNetwork() may block.
        new AsyncTask<String, Void, Void>() {
            @Override
            protected Void doInBackground(String... params) {
                Log.v("deletes network configurations for all live objects");
                final List<WifiConfiguration> configurations = mWifiManager.getConfiguredNetworks();

                // configurations can be null when WiFi is disabled
                if (configurations == null) {
                    return null;
                }

                for (WifiConfiguration configuration: configurations) {
                    String ssid = WifiManagerWrapper.unQuoteString(configuration.SSID);

                    Log.v("found a network configuration for '" + ssid + "'");
                    if (mDeviceIdTranslator.isLiveObject(ssid)) {
                        Log.v("deleting a network configuration for live object '" + ssid + "'");
                        WifiManagerWrapper.removeNetwork(mWifiManager, ssid);
                    }
                }

                return null;
            }
        }.execute();
    }

    class WifiReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case WifiManager.NETWORK_STATE_CHANGED_ACTION :
                    handleWifiConnection(intent);
                    break;

            }
        }

        private void handleWifiConnection(Intent intent) {
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            NetworkInfo.State state = networkInfo.getState();
            Log.v("networkInfo = " + networkInfo.toString());

            synchronized (WifiConnectionManager.class) {
                if (state.equals(NetworkInfo.State.CONNECTED) && mConnecting) {
                    String ssid = networkInfo.getExtraInfo();
                    if (ssid == null) {
                        // SSID in NetworkInfo may be null depending on the model of the device
                        ssid = mWifiManager.getConnectionInfo().getSSID();
                    }

                    ssid = WifiManagerWrapper.unQuoteString(ssid);
                    if (mDeviceIdTranslator.isLiveObject(ssid)) {
                        LiveObject connectedLiveObject = mDeviceIdTranslator.translateToLiveObject(ssid);
                        Log.d("connectedLiveObject = " + connectedLiveObject);
                        mNetworkListener.onConnected(connectedLiveObject.getLiveObjectName());

                        mConnecting = false;
                    }
                }
            }
        }
    }
}
