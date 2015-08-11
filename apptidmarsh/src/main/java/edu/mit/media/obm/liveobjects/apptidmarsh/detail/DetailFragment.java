package edu.mit.media.obm.liveobjects.apptidmarsh.detail;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Map;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.BindString;
import butterknife.ButterKnife;
import butterknife.OnClick;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.MLProjectContract;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.MLProjectPropertyProvider;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.ProfilePreference;
import edu.mit.media.obm.liveobjects.apptidmarsh.media.MediaViewActivity;
import edu.mit.media.obm.liveobjects.apptidmarsh.module.DependencyInjector;
import edu.mit.media.obm.liveobjects.apptidmarsh.utils.Util;
import edu.mit.media.obm.liveobjects.apptidmarsh.widget.BitmapEditor;
import edu.mit.media.obm.liveobjects.apptidmarsh.widget.ZoomInOutAnimation;
import edu.mit.media.obm.liveobjects.middleware.common.ContentId;
import edu.mit.media.obm.liveobjects.middleware.control.ContentController;
import edu.mit.media.obm.liveobjects.middleware.control.DbController;
import edu.mit.media.obm.liveobjects.middleware.util.JSONUtil;
import edu.mit.media.obm.shair.liveobjects.R;


/**
 * Created by Valerio Panzica La Manna on 09/01/15.
 * Shows the details of a connected live object and allows to play the content
 */
public class DetailFragment extends Fragment {

    private static final String LOG_TAG = DetailFragment.class.getSimpleName();
    //TODO make the directory name parametrizable
    @BindString(R.string.arg_live_object_name_id) String ARG_LIVE_OBJ_NAME_ID;
    @BindString(R.string.arg_connected_to_live_object) String ARG_CONNECTED_TO_LIVE_OBJ;
    @BindString(R.string.arg_live_object_name_id) String EXTRA_LIVE_OBJ_NAME_ID;
    @BindString(R.string.dir_contents) String DIRECTORY_NAME;
    @BindString(R.string.dir_comments) String COMMENT_DIRECTORY_NAME;

    private String mLiveObjectName;

    private View mRootView;

    @Inject ContentController mContentController;
    @Inject DbController mDbController;

    private OnErrorListener mOnErrorListener = null;

    private AsyncTask<String, Void, InputStream> mSetBackgroundImageTask = null;
    private AsyncTask<String, Void, JSONObject> mSetPropertiesTask = null;

    private AlertDialog mAddCommentAlert;
    private boolean mIsFavorite;
    private boolean mConnectedToLiveObject = false;

    @Bind(R.id.object_image_view) ImageView mIconView;
    @Bind(R.id.object_title_textview) TextView mObjectTitleTextView;
    @Bind(R.id.object_group_textview) TextView mObjectGroupTextView;
    @Bind(R.id.object_description_textview) TextView mObjectDescriptionTextView;
    @Bind(R.id.detail_progress_bar) ProgressBar mProgressBar;
    @Bind(R.id.detail_info_layout) LinearLayout mDetailInfoLayout;

    @Bind(R.id.favorite_button) LinearLayout mFavoriteButtonLayout;
    @Bind(R.id.addCommentButton) LinearLayout mAddCommentLayout;

    @BindString(R.string.media_config_filename) String mMediaConfigFileName;

    @OnClick(R.id.object_image_view)
    void onClickIconView() {
        // wait asynchronous tasks finish before starting another activity
        cancelAsyncTasks();
        // launch the media associated to the object
        Intent viewIntent = new Intent(getActivity(), MediaViewActivity.class);
        viewIntent.putExtra(EXTRA_LIVE_OBJ_NAME_ID, mLiveObjectName);
        getActivity().startActivity(viewIntent);
    }

    @OnClick(R.id.favorite_button)
    void onClickFavoriteButton() {
        // change the favorite state state
        mIsFavorite = !mIsFavorite;
        updateFavorite(mLiveObjectName, mIsFavorite);
        updateFavoriteUI(mFavoriteButtonLayout, mIsFavorite);
    }

    @OnClick(R.id.addCommentButton)
    void onClickAddCommentButton() {
        mAddCommentAlert.show();

        // TODO: changing button format should be done in initAddCommentAlert()
        Button positiveButton = mAddCommentAlert.getButton(DialogInterface.BUTTON_POSITIVE);
        Button negativeButton = mAddCommentAlert.getButton(DialogInterface.BUTTON_NEGATIVE);

        positiveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        positiveButton.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        negativeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        negativeButton.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    }


    public interface OnErrorListener {
        void onError(Exception exception);
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameter.
     *
     * @param liveObjectNameID the name_id of the live object obtained during discovery.
     * @return A new instance of fragment DetailFragment
     */
    public static DetailFragment newInstance(Activity activity, String liveObjectNameID, boolean showAddComment) {
        DetailFragment fragment = new DetailFragment();
        Bundle args = new Bundle();

        String argLiveObjectNameId = activity.getString(R.string.arg_live_object_name_id);
        String argConnectedToLiveObject = activity.getString(R.string.arg_connected_to_live_object);

        args.putString(argLiveObjectNameId, liveObjectNameID);
        args.putBoolean(argConnectedToLiveObject, showAddComment);
        fragment.setArguments(args);

        return fragment;
    }

    // Required empty constructor
    public DetailFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_detail, container, false);

        ButterKnife.bind(this, mRootView);
        DependencyInjector.inject(this, getActivity());

        Bundle arguments = getArguments();
        if (arguments != null) {
            mLiveObjectName = arguments.getString(ARG_LIVE_OBJ_NAME_ID);
            mConnectedToLiveObject = arguments.getBoolean(ARG_CONNECTED_TO_LIVE_OBJ);
        }

        Map<String, Object> liveObjectProperties = getLiveObjectProperties(mLiveObjectName);
        setUIContent(liveObjectProperties);

        return mRootView;
    }

    private Map<String, Object> getLiveObjectProperties(String liveObjectId) {
        Map<String, Object> properties = null;

        if (mDbController.isLiveObjectEmpty(liveObjectId)) {
            // live object empty, fill it with properties
            properties = fetchProperties(liveObjectId);
            storeProperties(liveObjectId, properties);
        } else {
            properties = mDbController.getProperties(liveObjectId);
        }

        return properties;
    }

    private Map<String, Object> fetchProperties(final String liveObjectId) {
        String mediaConfigFileName = mMediaConfigFileName + ".jso";

        mSetPropertiesTask =
                new AsyncTask<String, Void, JSONObject>() {
                    @Override
                    protected JSONObject doInBackground(String... params) {
                        String configFileName = params[0];

                        InputStream inputStream = null;
                        try {

                            ContentId configFileContentId = new ContentId(liveObjectId, DIRECTORY_NAME, configFileName);
                            // retrieve JSON Object
                            inputStream = mContentController.getInputStreamContent(configFileContentId);

                            JSONObject jsonConfig = JSONUtil.getJSONFromInputStream(inputStream);
                            inputStream.close();
                            return jsonConfig;

                        } catch (Exception e) {
                            e.printStackTrace();
                            mOnErrorListener.onError(e);
                        }
                        return null;
                    }
                }.execute(mediaConfigFileName);


        Map<String, Object> properties = null;
        try {
            JSONObject jsonProperties = mSetPropertiesTask.get();
            properties = JSONUtil.jsonToMap(jsonProperties);

            // add the isFavorite property, which is not present in the remote live-object,
            // and initialize it to false
            properties.put(MLProjectContract.IS_FAVORITE, MLProjectContract.IS_FAVORITE_FALSE);
        } catch (Exception e) {
            mOnErrorListener.onError(e);
        }

        return properties;

    }

    private void storeProperties(String liveObjectId, Map<String, Object> properties) {
        Log.d(LOG_TAG, "storing properties " + properties);
        mDbController.putLiveObject(liveObjectId, properties);
    }


    private void setUIContent(Map<String, Object> liveObjectProperties) {
        Log.d(LOG_TAG, liveObjectProperties.toString());

        MLProjectPropertyProvider propertyProvider = new MLProjectPropertyProvider(liveObjectProperties);

        String title = propertyProvider.getProjectTitle();
        String group = propertyProvider.getProjectGroup();
        String description = propertyProvider.getProjectDescription();
        String mediaFileName = propertyProvider.getMediaFileName();

        mObjectTitleTextView.setText(title);
        mObjectGroupTextView.setText(group);
        mObjectDescriptionTextView.setText(description);

        String imageFileName = propertyProvider.getIconFileName();
        setBackgroundImage(imageFileName);

        mIsFavorite = setFavoriteButtonState(mFavoriteButtonLayout, propertyProvider);
        mAddCommentAlert = initAddCommentAlert();

        mAddCommentLayout.setEnabled(mConnectedToLiveObject);

        // check if the media content of the target live object is locally available or downloadable
        ContentId mediaContentId = new ContentId(mLiveObjectName, DIRECTORY_NAME, mediaFileName);
        boolean contentReachable =
                mContentController.isContentLocallyAvailable(mediaContentId) || mConnectedToLiveObject;
        if (contentReachable) {
            mIconView.setEnabled(true);
            mIconView.setVisibility(View.VISIBLE);
        } else {
            mIconView.setEnabled(false);
            mIconView.setVisibility(View.GONE);
        }
    }

    private boolean setFavoriteButtonState(LinearLayout favouriteButtonLayout, MLProjectPropertyProvider propertyProvider) {
        boolean isFavorite = propertyProvider.isFavorite();

        updateFavoriteUI(favouriteButtonLayout, isFavorite);

        return isFavorite;
    }

    private void updateFavoriteUI(LinearLayout favouriteButtonLayout, boolean isFavorite) {
        int backgroundColorId = (isFavorite ? R.color.theme_transparent_orange : R.color.theme_pure_transparent_background);
        int backgroundColor = getResources().getColor(backgroundColorId);

        favouriteButtonLayout.setBackgroundColor(backgroundColor);
    }

    private void updateFavorite(String liveObjNameId, boolean isFavorite) {

        int isFavoriteInInt = isFavorite ? MLProjectContract.IS_FAVORITE_TRUE :
                MLProjectContract.IS_FAVORITE_FALSE;
        Log.d(LOG_TAG, "update property is favorite to: " + isFavoriteInInt);
        mDbController.putProperty(liveObjNameId, MLProjectContract.IS_FAVORITE, new Integer(isFavoriteInInt));
        Log.d(LOG_TAG, "now favorite is: " + mDbController.getProperty(liveObjNameId, MLProjectContract.IS_FAVORITE));
    }

    private AlertDialog initAddCommentAlert() {
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());

        TextView titleTextView = new TextView(new ContextThemeWrapper(getActivity(), R.style.LiveObjectsTextViewStyle));
        titleTextView.setText(" Add Comment");
        alert.setCustomTitle(titleTextView);

        // Set an EditText view to get the user input
        final EditText input = new EditText(new ContextThemeWrapper(getActivity(), R.style.LiveObjectsEditTextStyle));
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        input.setHint("Type your comment here");
        alert.setView(input);

        alert.setPositiveButton("Send", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                String commentText = makeComment(input.getText().toString());
                Log.d(LOG_TAG, "ADDING COMMENT: " + input.getText().toString());
                ContentId commentContentId = new ContentId(mLiveObjectName, COMMENT_DIRECTORY_NAME, generateCommentFileName());
                mContentController.putStringContent(commentContentId, commentText);

                input.setText("");
                Toast.makeText(getActivity(), "Uploaded a comment", Toast.LENGTH_SHORT).show();
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
            }
        });

        AlertDialog dialog = alert.create();

        return dialog;
    }

    private String makeComment(String text) {
        SharedPreferences pref = ProfilePreference.getInstance(getActivity());
        String name = "Name: " + ProfilePreference.getString(pref, getActivity(), R.string.profile_name_key) + "\n";
        String company = "Organization: " + ProfilePreference.getString(pref, getActivity(), R.string.profile_company_key) + "\n";
        String email = "Email: " + ProfilePreference.getString(pref, getActivity(), R.string.profile_email_key) + "\n";
        String commentHeader = "Comment: \n";
        String message = name + company + email + commentHeader + text;
        return message;
    }

    private String generateCommentFileName() {
        Calendar rightNow = Calendar.getInstance();
        String commentName = String.format("%1$td%1$tk%1$tM%1$tS.TXT", rightNow);

        return commentName;
    }

    private void setBackgroundImage(String imageFileName) {
        mSetBackgroundImageTask = new AsyncTask<String, Void, InputStream>() {
            @Override
            protected InputStream doInBackground(String... params) {
                Log.v(LOG_TAG, "doInBackground()");
                String imageFileName = params[0];
                ContentId imageContentId = new ContentId(mLiveObjectName, DIRECTORY_NAME, imageFileName);

                try {
                    InputStream imageInputStream = mContentController.getInputStreamContent(imageContentId);
                    return imageInputStream;
                } catch (Exception e) {
                    e.printStackTrace();
                    mOnErrorListener.onError(e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(InputStream imageInputStream) {
                Log.v(LOG_TAG, "onPostExecute()");

                try {
                    Bitmap bitmap = Util.getBitmap(imageInputStream);
                    setBackgroundImage(bitmap);
                    imageInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    mOnErrorListener.onError(e);
                }
            }
        }.execute(imageFileName);

    }

    private void setBackgroundImage(Bitmap bitmap) {

        Activity activity = DetailFragment.this.getActivity();

        BitmapEditor bitmapEditor = new BitmapEditor(activity);

        if (bitmap != null) {
            Bitmap croppedBitmap = bitmapEditor.cropToDisplayAspectRatio(bitmap, activity.getWindowManager());
            bitmapEditor.blurBitmap(croppedBitmap, 2);
            BitmapDrawable background = new BitmapDrawable(croppedBitmap);
            mRootView.setBackgroundDrawable(background);

        }
        mProgressBar.setVisibility(View.GONE);
        mDetailInfoLayout.setVisibility(View.VISIBLE);
    }

    protected void cancelAsyncTasks() {

        if (mSetBackgroundImageTask != null) {
            mSetBackgroundImageTask.cancel(true);
        }
        if (mSetPropertiesTask != null) {
            mSetPropertiesTask.cancel(true);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        mIconView.clearAnimation();
    }

    @Override
    public void onStart() {
        super.onStart();
        ZoomInOutAnimation zoomInOutAnimation = new ZoomInOutAnimation(mIconView, getActivity());
        zoomInOutAnimation.startAnimation();
    }

    public void setOnCancelListener(OnErrorListener onCancelListener) {
        mOnErrorListener = onCancelListener;
    }
}
