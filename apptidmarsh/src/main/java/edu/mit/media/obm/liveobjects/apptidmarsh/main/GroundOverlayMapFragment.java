package edu.mit.media.obm.liveobjects.apptidmarsh.main;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.eventbus.Subscribe;
import com.squareup.otto.Bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import butterknife.BindString;
import butterknife.ButterKnife;
import edu.mit.media.obm.liveobjects.apptidmarsh.detail.DetailActivity;
import edu.mit.media.obm.liveobjects.apptidmarsh.module.DependencyInjector;
import edu.mit.media.obm.liveobjects.apptidmarsh.utils.InactiveLiveObjectDetectionEvent;
import edu.mit.media.obm.liveobjects.apptidmarsh.utils.LiveObjectNotifier;
import edu.mit.media.obm.liveobjects.apptidmarsh.widget.MenuActions;
import edu.mit.media.obm.liveobjects.middleware.common.LiveObject;
import edu.mit.media.obm.liveobjects.middleware.control.ConnectionListener;
import edu.mit.media.obm.liveobjects.middleware.control.DiscoveryListener;
import edu.mit.media.obm.liveobjects.middleware.control.NetworkController;
import edu.mit.media.obm.shair.liveobjects.R;

/**
 * Created by artimo14 on 8/9/15.
 */
public class GroundOverlayMapFragment extends SupportMapFragment {
    private static final String LOG_TAG = GroundOverlayMapFragment.class.getSimpleName();

    private static final int DETAIL_ACTIVITY_REQUEST_CODE = 1;

    private final int NUM_GRID_X = 256;
    private final int NUM_GRID_Y = 256;
    private final int NUM_MAP_ID = 16;

    @Inject NetworkController mNetworkController;
    @Inject LiveObjectNotifier mLiveObjectNotifier;
    @Inject Bus mBus;

    @BindString(R.string.arg_live_object_name_id) String EXTRA_LIVE_OBJ_NAME_ID;
    @BindString(R.string.arg_connected_to_live_object) String EXTRA_CONNECTED_TO_LIVE_OBJ;

    private ProgressDialog mConnectingDialog;

    private GoogleMap mMap;

    private ArrayList<LiveObject> mLiveObjectList = new ArrayList<>();
    private ArrayList<LiveObject> mActiveLiveObjectList = new ArrayList<>();
    private ArrayList<LiveObject> mSleepingLiveObjectList = new ArrayList<>();
    private LiveObject mSelectedLiveObject;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = super.onCreateView(inflater, container, savedInstanceState);

        ButterKnife.bind(this, rootView);
        DependencyInjector.inject(this, getActivity());

        setupUIElements();
        setUpMap();

        return rootView;
    }

    private void setUpMap() {
        mMap = getMap();

        MarkerOptions markerOptions = new MarkerOptions()
                .position(new LatLng(0, 0))
                .title("Marker");
        mMap.addMarker(markerOptions);

        final LatLng overlayPosition = new LatLng(0, 0);
        BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.main_map);

        mMap.setMapType(GoogleMap.MAP_TYPE_NONE);
        GroundOverlayOptions newarkMap = new GroundOverlayOptions()
                .image(bitmapDescriptor)
                .position(overlayPosition, 1000f, 1000f);
        mMap.addGroundOverlay(newarkMap);

        CustomCameraChangeListener customCameraChangeListener =
                new CustomCameraChangeListener(mMap, 16, 18, new LatLng(-0.005, -0.005), new LatLng(0.005, 0.005));
        mMap.setOnCameraChangeListener(customCameraChangeListener);
    }

    public void addLiveObjectMarker(String liveObjectName, int gridX, int gridY, int mapId) {
        checkArgumentRange("gridX", gridX, 0, NUM_GRID_X - 1);
        checkArgumentRange("gridY", gridY, 0, NUM_GRID_Y - 1);
        checkArgumentRange("mapId", mapId, 0, NUM_MAP_ID - 1);

        LatLng gridLocationInLagLng = gridToLatLng(gridX, gridY);
        MarkerOptions markerOptions = new MarkerOptions()
                .position(gridLocationInLagLng)
                .title(liveObjectName);
        mMap.addMarker(markerOptions);
    }

    private void checkArgumentRange(String argName, int argValue, int minVaue, int maxValue) {
        if (argValue < minVaue || argValue > maxValue) {
            String errorMessage = String.format("arg %s (%d) is out of the range '%d <= %s <= %d'",
                    argName, argValue, minVaue, argName, maxValue);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private LatLng gridToLatLng(int grid_x, int grid_y) {
        return new LatLng(0.0f, 0.0f);
    }

    private void setupUIElements() {
        /*
        mAdapter = new AnimationArrayAdapter(getActivity(), R.layout.list_item_live_objects,
                mLiveObjectNamesList);
        mLiveObjectsGridView.setAdapter(mAdapter);
        mSwipeLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        */
        mConnectingDialog = new ProgressDialog(getActivity());
        mConnectingDialog.setIndeterminate(true);
        mConnectingDialog.setCancelable(true);
        mConnectingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                mNetworkController.cancelConnecting();
            }
        });
    }

    @Override
    public void onStart() {
        Log.v(LOG_TAG, "onStart()");
        super.onStart();

        mBus.register(this);

        mNetworkController.start();
        mNetworkController.startDiscovery();

        mSleepingLiveObjectList.clear();
        mLiveObjectNotifier.wakeUp();
    }

    @Override
    public void onStop() {
        Log.v(LOG_TAG, "onStop()");
//        mNetworkController.stop();
        super.onStop();

        mBus.unregister(this);

        mLiveObjectNotifier.cancelWakeUp();
    }

    private static class CustomCameraChangeListener implements GoogleMap.OnCameraChangeListener {
        private GoogleMap mMap;
        private float mMaxZoom;
        private float mMinZoom;

        private LatLng mSouthWestBound;
        private LatLng mNorthEastBound;

        public CustomCameraChangeListener(GoogleMap map, float minZoom, float maxZoom, LatLng southWestBound, LatLng northEastBound) {
            mMap = map;

            mMaxZoom = maxZoom;
            mMinZoom = minZoom;
            mSouthWestBound = southWestBound;
            mNorthEastBound = northEastBound;
        }

        @Override
        public void onCameraChange(CameraPosition cameraPosition) {
            if (cameraPosition.zoom > mMaxZoom) {
                mMap.moveCamera(CameraUpdateFactory.zoomTo(mMaxZoom));
            }

            if (cameraPosition.zoom < mMinZoom) {
                mMap.moveCamera(CameraUpdateFactory.zoomTo(mMinZoom));
            }

            if (cameraPosition.target.latitude < mSouthWestBound.latitude) {
                LatLng latLng = new LatLng(mSouthWestBound.latitude, cameraPosition.target.longitude);
                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            }

            if (cameraPosition.target.longitude < mSouthWestBound.longitude) {
                LatLng latLng = new LatLng(cameraPosition.target.latitude, mSouthWestBound.longitude);
                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            }

            if (cameraPosition.target.latitude > mNorthEastBound.latitude) {
                LatLng latLng = new LatLng(mNorthEastBound.latitude, cameraPosition.target.longitude);
                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            }

            if (cameraPosition.target.longitude > mNorthEastBound.longitude) {
                LatLng latLng = new LatLng(cameraPosition.target.latitude, mNorthEastBound.longitude);
                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            }

            Log.v(LOG_TAG, cameraPosition.toString());
        }
    }


    private void initNetworkListeners() {
        initDiscoveryListener();
        initConnectionListener();

        Log.v(LOG_TAG, "deleting all the network configuration related to live objects");
        if (!mNetworkController.isConnecting()) {
            mNetworkController.forgetNetworkConfigurations();
        }

//        mAdapter.notifyDataSetChanged();
    }

    private void initDiscoveryListener() {
        mNetworkController.setDiscoveryListener(new DiscoveryListener() {
            @Override
            public void onDiscoveryStarted() {
                Log.d(LOG_TAG, "discovery started");
            }

            @Override
            public void onLiveObjectsDiscovered(List<LiveObject> liveObjectList) {
                Log.d(LOG_TAG, "discovery successfully completed");
                mActiveLiveObjectList.clear();
                for (LiveObject liveObject : liveObjectList) {
                    mActiveLiveObjectList.add(liveObject);
                }

                updateLiveObjectsList();
            }
        });
    }

    private void updateLiveObjectsList() {
        mLiveObjectList.clear();
        mLiveObjectList.addAll(mActiveLiveObjectList);

        // add only ones in active list if the same live object exists both in active and in
        // sleeping lists.
        // ToDo: should use Set<T>
        for (LiveObject liveObject : mSleepingLiveObjectList) {
            boolean inActiveList = false;

            for (LiveObject activeLiveObject : mActiveLiveObjectList) {
                if (liveObject.getLiveObjectName().equals(activeLiveObject.getLiveObjectName())) {
                    inActiveList = true;
                    break;
                }
            }

            if (!inActiveList) {
                mLiveObjectList.add(liveObject);
            }
        }

        /*
        mAdapter.notifyDataSetChanged();
        mSwipeLayout.setRefreshing(false);
        */
    }

    private void initConnectionListener() {
        /*
        mNetworkController.setConnectionListener(new ConnectionListener() {
            @Override
            public void onConnected(LiveObject connectedLiveObject) {
                Log.v(LOG_TAG, String.format("onConnected(%s)", connectedLiveObject));
                if (connectedLiveObject.equals(mSelectedLiveObject)) {
                    mConnectingDialog.dismiss();

                    final TextView liveObjectTitleTextView =
                            ButterKnife.findById(mClickedView, R.id.grid_item_title_textview);

                    Animation animation = new ExpandIconAnimation(
                            getActivity().getWindowManager(), mClickedView).getAnimation();
                    animation.setFillAfter(true);
                    animation.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            // doesn't show the title of a live object to prevent a strange error
                            // regarding too huge texts when the icon is expanding on an emulator.
                            Log.v(LOG_TAG, "onAnimationStart()");
                            liveObjectTitleTextView.setVisibility(View.GONE);

                            mSwipeLayout.setClipChildren(false);
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            Log.v(LOG_TAG, "onAnimationEnd()");

                            // when the selected live objected is connected
                            // start the corresponding detail activity
                            Intent detailIntent = new Intent(getActivity(), DetailActivity.class);
                            detailIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

                            detailIntent.putExtra(EXTRA_LIVE_OBJ_NAME_ID, mSelectedLiveObject.getLiveObjectName());
                            detailIntent.putExtra(EXTRA_CONNECTED_TO_LIVE_OBJ, true);
                            startActivityForResult(detailIntent, DETAIL_ACTIVITY_REQUEST_CODE);
                            mSelectedLiveObject = null;
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    });

                    // make views other than the clicked one invisible for z-ordering problems
                    ViewGroup viewGroup = ((ViewGroup) mClickedView.getParent());
                    int clickedIndex = viewGroup.indexOfChild(mClickedView);
                    for (int i = 0; i < viewGroup.getChildCount(); i++) {
                        if (i != clickedIndex) {
                            viewGroup.getChildAt(i).setVisibility(View.INVISIBLE);
                        }
                    }

                    mClickedView.startAnimation(animation);
                    Log.v(LOG_TAG, "starting an animation");
                }

            }
        });
        */
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.v(LOG_TAG, String.format("onActivityResult(requestCode=%d)", requestCode));
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == DETAIL_ACTIVITY_REQUEST_CODE) {
            Log.v(LOG_TAG, "returned from DetailActivity");
            final String errorMessage;

            if (resultCode == DetailActivity.RESULT_CONNECTION_ERROR) {
                errorMessage = "a network error in the live object";
            } else if (resultCode == DetailActivity.RESULT_JSON_ERROR) {
                errorMessage = "An error in the contents in the live object";
            } else {
                errorMessage = null;
            }

            if (errorMessage != null) {
                getActivity().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        });

                // recreate the MainActivity to reset the UI state
                MenuActions.goToHome(getActivity());
            }
        }
    }

    @Subscribe
    public void addDetectedBluetoothDevice(InactiveLiveObjectDetectionEvent event) {
        Log.v(LOG_TAG, "addDetectedBluetoothDevice()");
        LiveObject liveObject = new LiveObject(event.mDeviceName);
        liveObject.setActive(false);
        mSleepingLiveObjectList.add(liveObject);

        updateLiveObjectsList();
    }
}