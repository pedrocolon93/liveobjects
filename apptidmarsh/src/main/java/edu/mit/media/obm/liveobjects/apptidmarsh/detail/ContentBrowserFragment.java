package edu.mit.media.obm.liveobjects.apptidmarsh.detail;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.noveogroup.android.log.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.BindString;
import butterknife.ButterKnife;
import butterknife.OnItemClick;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.MLProjectContract;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.MLProjectPropertyProvider;
import edu.mit.media.obm.liveobjects.apptidmarsh.module.DependencyInjector;
import edu.mit.media.obm.liveobjects.middleware.common.ContentId;
import edu.mit.media.obm.liveobjects.middleware.control.ContentController;
import edu.mit.media.obm.liveobjects.middleware.control.DbController;
import edu.mit.media.obm.liveobjects.middleware.util.JSONUtil;
import edu.mit.media.obm.shair.liveobjects.R;

/**
 * Created by artimo14 on 8/19/15.
 */
public class ContentBrowserFragment extends Fragment {
    @BindString(R.string.media_config_filename) String MEDIA_CONFIG_FILE_NAME;
    @BindString(R.string.dir_contents) String DIRECTORY_NAME;
    @BindString(R.string.arg_live_object_name_id) String ARG_LIVE_OBJ_NAME_ID;
    @BindString(R.string.arg_connected_to_live_object) String ARG_CONNECTED_TO_LIVE_OBJ;
    @BindString(R.string.arg_live_object_name_id) String EXTRA_LIVE_OBJ_NAME_ID;
    @BindString(R.string.arg_connected_to_live_object) String EXTRA_CONNECTED_TO_LIVE_OBJ;
    @BindString(R.string.arg_content_index) String EXTRA_CONTENT_INDEX;
    @BindString(R.string.extra_arguments) String EXTRA_ARGUMENTS;

    @Bind(R.id.content_list_view) ListView mContentListView;

    @Inject ContentController mContentController;
    @Inject DbController mDbController;

    @OnItemClick(R.id.content_list_view)
    void onContentItemClick(int position) {
        Bundle arguments = new Bundle();
        arguments.putString(EXTRA_LIVE_OBJ_NAME_ID, mLiveObjectName);
        arguments.putInt(EXTRA_CONTENT_INDEX, position);
        arguments.putBoolean(EXTRA_CONNECTED_TO_LIVE_OBJ, true);

        Intent intent = new Intent(getActivity(), DetailActivity.class);
        intent.putExtra(EXTRA_ARGUMENTS, arguments);
        startActivity(intent);
    }

    String mLiveObjectName;
    boolean mConnectedToLiveObject;


    private DetailFragment.OnErrorListener mOnErrorListener = null;

    private JsonRetrievalTask mSetPropertiesTask = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_content_browser, container, false);

        ButterKnife.bind(this, rootView);
        DependencyInjector.inject(this, getActivity());

        Bundle arguments = getArguments();
        if (arguments != null) {
            mLiveObjectName = arguments.getString(ARG_LIVE_OBJ_NAME_ID);
            mConnectedToLiveObject = arguments.getBoolean(ARG_CONNECTED_TO_LIVE_OBJ);
        }

        try {
            Map<String, Object> liveObjectProperties = fetchProperties(mLiveObjectName);
            mDbController.putLiveObject(mLiveObjectName, liveObjectProperties);
            setUIContent(mDbController.getProperties(mLiveObjectName));
        } catch (Exception e) {
            Log.e(e);
            getActivity().finish();
        }

        return rootView;
    }

    private Map<String, Object> fetchProperties(final String liveObjectId) throws Exception {
        String mediaConfigFileName = MEDIA_CONFIG_FILE_NAME + ".jso";

        mSetPropertiesTask = new JsonRetrievalTask(liveObjectId);
        mSetPropertiesTask.execute(mediaConfigFileName);

        Map<String, Object> properties = null;

        // wait for completion of async task
        JSONObject jsonProperties = mSetPropertiesTask.get();
        Exception exception = mSetPropertiesTask.getException();
        if (exception != null) {
            throw exception;
        }

        properties = JSONUtil.jsonToMap(jsonProperties);

        // add the isFavorite property, which is not present in the remote live-object,
        // and initialize it to false
        properties.put(MLProjectContract.IS_FAVORITE, MLProjectContract.IS_FAVORITE_FALSE);

        return properties;
    }

    private class JsonRetrievalTask extends AsyncTask<String, Void, JSONObject> {
        private Exception mException = null;
        private final String mLiveObjectId;

        public JsonRetrievalTask(String liveObjectId) {
            mLiveObjectId = liveObjectId;
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            String configFileName = params[0];

            try {
                Log.v("start downloading JSON");
                ContentId configFileContentId = new ContentId(mLiveObjectId, DIRECTORY_NAME, configFileName);
                // retrieve JSON Object
                InputStream inputStream = mContentController.getInputStreamContent(configFileContentId);

                JSONObject jsonConfig = JSONUtil.getJSONFromInputStream(inputStream);
                inputStream.close();

                Log.v("finished downloading JSON");
                return jsonConfig;
            } catch (Exception e) {
                // keep exception object for later use
                mException = e;
                return null;
            }
        }

        public Exception getException() {
            return mException;
        }
    }

    private void setUIContent(Map<String, Object> liveObjectProperties) {
        MLProjectPropertyProvider provider = new MLProjectPropertyProvider(liveObjectProperties);

        ListAdapter adapter = new ContentBrowserAdapter(getActivity(), provider);
        mContentListView.setAdapter(adapter);

        // set activity's title from this fragment
        getActivity().setTitle(mLiveObjectName);
    }

    protected void cancelAsyncTasks() {
        if (mSetPropertiesTask != null) {
            mSetPropertiesTask.cancel(true);
        }
    }

    public void setOnCancelListener(DetailFragment.OnErrorListener onCancelListener) {
        mOnErrorListener = onCancelListener;
    }
}
