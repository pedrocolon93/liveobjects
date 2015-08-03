package edu.mit.media.obm.liveobjects.apptidmarsh.detail;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Map;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dagger.ObjectGraph;
import edu.mit.media.obm.liveobjects.apptidmarsh.LiveObjectsApplication;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.MLProjectContract;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.MLProjectPropertyProvider;
import edu.mit.media.obm.liveobjects.apptidmarsh.data.ProfilePreference;
import edu.mit.media.obm.liveobjects.apptidmarsh.media.MediaViewActivity;
import edu.mit.media.obm.liveobjects.apptidmarsh.widget.BitmapEditor;
import edu.mit.media.obm.liveobjects.middleware.common.ContentId;
import edu.mit.media.obm.liveobjects.middleware.control.ContentController;
import edu.mit.media.obm.liveobjects.middleware.control.DbController;
import edu.mit.media.obm.shair.liveobjects.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link WrapUpFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WrapUpFragment extends Fragment {
    private static final String LOG_TAG = WrapUpFragment.class.getSimpleName();
    // TODO make the comment directory names parametrizable
    private static final String COMMENT_DIRECTORY_NAME = "COMMENTS";
    private static final String MEDIA_DIR_NAME = "DCIM";

    private static final String ARG_LIVE_OBJ_NAME_ID = "live_obj_name_id";
    private static final String ARG_SHOW_ADD_COMMENT = "show_add_comment";

    private String mLiveObjNameId;
    private boolean mShowAddComment;

    @Inject DbController mDbController;
    @Inject ContentController mContentController;

    @Bind(R.id.wrapup_title_textview) TextView mTitleTextView;
    @Bind(R.id.wrapup_group_textview) TextView mGroupTextView;
    @Bind(R.id.wrapup_description_textview) TextView mDescriptionTextView;
    @Bind(R.id.favorite_button) LinearLayout mFavouriteButtonLayout;
    @Bind(R.id.replay_button) LinearLayout mReplayButtonLayout;
    @Bind(R.id.addCommentButton) LinearLayout mAddCommentLayout;

    @OnClick(R.id.favorite_button)
    void onClickFavoriteButton() {
        // change the favorite state state
        mIsFavorite = !mIsFavorite;
        updateFavorite(mLiveObjNameId, mIsFavorite);
        updateFavoriteUI(mFavouriteButtonLayout, mIsFavorite);
    }


    @OnClick(R.id.replay_button)
    void onClickReplayButton() {
        Intent viewIntent = new Intent(getActivity(), MediaViewActivity.class);
        viewIntent.putExtra(MediaViewActivity.EXTRA_LIVE_OBJ_NAME_ID, mLiveObjNameId);
        getActivity().startActivity(viewIntent);
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

    AlertDialog mAddCommentAlert;

    private boolean mIsFavorite;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment WrapUpFragment.
     */
    public static WrapUpFragment newInstance(String liveObjNameId, boolean showAddComment) {
        WrapUpFragment fragment = new WrapUpFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LIVE_OBJ_NAME_ID, liveObjNameId);
        args.putBoolean(ARG_SHOW_ADD_COMMENT, showAddComment);
        fragment.setArguments(args);
        return fragment;
    }

    public WrapUpFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LiveObjectsApplication app = (LiveObjectsApplication) getActivity().getApplication();
        app.injectObjectGraph(this);

        if (getArguments() != null) {
            mLiveObjNameId = getArguments().getString(ARG_LIVE_OBJ_NAME_ID);
            mShowAddComment = getArguments().getBoolean(ARG_SHOW_ADD_COMMENT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_wrap_up, container, false);
        ButterKnife.bind(this, rootView);

        mAddCommentAlert = initAddCommentAlert();
        setUIContent(rootView);

        return rootView;
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
                ContentId commentContentId = new ContentId(mLiveObjNameId, COMMENT_DIRECTORY_NAME, generateCommentFileName());
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


    private void setUIContent(View rootView) {

        Map<String, Object> properties = mDbController.getProperties(mLiveObjNameId);
        MLProjectPropertyProvider propertyProvider = new MLProjectPropertyProvider(properties);
        mTitleTextView.setText(propertyProvider.getProjectTitle());
        mGroupTextView.setText(propertyProvider.getProjectGroup());
        mDescriptionTextView.setText(propertyProvider.getProjectDescription());
        setImage(rootView, propertyProvider.getIconFileName());

        mAddCommentLayout.setEnabled(mShowAddComment);
        mIsFavorite = setFavoriteButtonState(mFavouriteButtonLayout, propertyProvider);
        mReplayButtonLayout.setEnabled(true);
// TODO ?
//        mReplayButtonLayout.setEnabled(isContentStoredLocally());
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

    private void setImage(View view, String fileName) {
        Activity activity = getActivity();

        ContentId imageContentId = new ContentId(mLiveObjNameId, MEDIA_DIR_NAME, fileName);

        try {
            Bitmap bitmap = BitmapFactory.decodeFile(mContentController.getFileUrl(imageContentId));

            BitmapEditor bitmapEditor = new BitmapEditor(activity);
            Bitmap croppedBitmap = bitmapEditor.cropToDisplayAspectRatio(bitmap, activity.getWindowManager());
            bitmapEditor.blurBitmap(croppedBitmap, 2);

            if (croppedBitmap != null) {
                BitmapDrawable background = new BitmapDrawable(croppedBitmap);
                view.setBackgroundDrawable(background);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "error setting image", e);
        }
    }


}
