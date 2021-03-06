package edu.mit.media.obm.livobjects.commentsviewer;


import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.noveogroup.android.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import edu.mit.media.obm.liveobjects.driver.wifi.WifiConnectionManager;
import edu.mit.media.obm.liveobjects.middleware.common.ContentId;
import edu.mit.media.obm.liveobjects.middleware.common.LiveObjectsMiddleware;
import edu.mit.media.obm.liveobjects.middleware.common.MiddlewareInterface;
import edu.mit.media.obm.liveobjects.middleware.control.ContentBridge;
import edu.mit.media.obm.liveobjects.middleware.control.ContentController;
import edu.mit.media.obm.liveobjects.middleware.control.NetworkBridge;
import edu.mit.media.obm.liveobjects.middleware.control.NetworkController;
import edu.mit.media.obm.liveobjects.middleware.net.NetworkConnectionManager;
import edu.mit.media.obm.liveobjects.middleware.storage.LocalStorageDriver;
import edu.mit.media.obm.liveobjects.middleware.storage.RemoteStorageDriver;
import edu.mit.media.obm.liveobjects.storage.wifi.WifiStorageDriver;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainFragment extends Fragment {
    private TextView mCommentTextView;

    private static final String COMMENT_FOLDER = "COMMENTS";
    //TODO retrieving the id of the liveObject
    private static final String DUMMY_LIVE_OBJECT_ID = "dummyLiveObject";

    private MiddlewareInterface mMiddleware;
    private ContentController mContentController;
    private Handler mUpdateCommentHandler;

    private static final int SECONDS = 1000;
    private static final int MINUTES = 60 * SECONDS;
    private final int mUpdateListInterval = 1 * MINUTES;
    private final int mUpdateCommentInterval = 5 * SECONDS;

    private AsyncTask<String,Void, List<String>> mUpdateCommentListTask;



    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @return A new instance of fragment MainFragment.
     */

    public static MainFragment newInstance() {
        MainFragment fragment = new MainFragment();
        return fragment;
    }

    public MainFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMiddleware = createMiddleware();
        mContentController = mMiddleware.getContentController();

        mUpdateCommentHandler = new Handler();
        mUpdateCommentListTask = createUpdateCommentListTask();



    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mCommentTextView = (TextView) rootView.findViewById(R.id.comment_textview);

        mUpdateCommentListTask.execute(COMMENT_FOLDER);
        return rootView;
    }



    private Runnable createUpdateCommandRunnable (final int index, final List<String> commentList) {
        Runnable updateComment = new Runnable() {
            @Override
            public void run() {
                if (index >= commentList.size()) {
                    if (mUpdateCommentListTask != null ) {
                        mUpdateCommentListTask = createUpdateCommentListTask();
                        mUpdateCommentListTask.execute(COMMENT_FOLDER);
                        return;
                    }
                }

                String comment = commentList.get(index);
                mCommentTextView.setText(comment);
                mUpdateCommentHandler.postDelayed(createUpdateCommandRunnable(index + 1, commentList), mUpdateCommentInterval);
            }
        };
        return updateComment;

    }



    private AsyncTask<String,Void, List<String>> createUpdateCommentListTask() {
        return new AsyncTask<String, Void, List<String>>() {
            @Override
            protected List<String> doInBackground(String... params) {
                String dir = params[0];

                List<String> fileNameList =  mContentController.getFileNamesOfADirectory(DUMMY_LIVE_OBJECT_ID, dir);
                List<String> commentList = new ArrayList<>();
                for (String filename : fileNameList) {

                    try {
                        ContentId commentContentId =new ContentId(DUMMY_LIVE_OBJECT_ID, COMMENT_FOLDER, filename);
                        InputStream inputStream = mContentController.getInputStreamContent(commentContentId);
                        BufferedReader bufreader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                        StringBuffer strbuf = new StringBuffer();
                        String str;
                        while ((str = bufreader.readLine()) != null) {
                            if(strbuf.toString() != "") strbuf.append("\n");
                            strbuf.append(str);
                        }
                        String stringContent = strbuf.toString();
                        String comment = extractComment(stringContent);
                        commentList.add(comment);
                    } catch (IOException e) {
                        Log.e("", e);
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        Log.e("", e);
                    }

                }
                return commentList;
            }

            @Override
            protected void onPostExecute(List<String> commentList) {

                super.onPostExecute(commentList);
                getActivity().runOnUiThread(
                        createUpdateCommandRunnable(0, commentList)
                );


            }
        };

    }

    private String extractComment(String content) {
        return content.substring(content.indexOf("Comment:") + 8);
    }

    private MiddlewareInterface createMiddleware() {
        NetworkConnectionManager networkConnectionManager = new WifiConnectionManager(getActivity());
        NetworkController networkController = new NetworkBridge(networkConnectionManager);
        LocalStorageDriver localStorageDriver = null;

        RemoteStorageDriver remoteStorageDriver = null;
        remoteStorageDriver = new WifiStorageDriver(getActivity());

        ContentController contentController = new ContentBridge(getActivity(), localStorageDriver, remoteStorageDriver);
        // for this application we set DbController at null since we do not need it.
        return new LiveObjectsMiddleware(networkController, contentController, null);
    }


}
