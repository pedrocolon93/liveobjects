package edu.mit.media.obm.liveobjects.apptidmarsh.detail;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;

import org.json.JSONException;

import java.net.ConnectException;

import butterknife.BindString;
import edu.mit.media.obm.liveobjects.apptidmarsh.widget.MenuActions;
import edu.mit.media.obm.liveobjects.apptidmarsh.widget.SingleFragmentActivity;
import edu.mit.media.obm.shair.liveobjects.R;

/**
 * Created by artimo14 on 8/19/15.
 */
public class ContentBrowserActivity extends SingleFragmentActivity {
    @BindString(R.string.extra_arguments) String EXTRA_ARGUMENTS;

    ContentBrowserFragment mFragment;

    @Override
    protected Fragment createFragment() {
        mFragment = new ContentBrowserFragment();

        Bundle arguments = getIntent().getBundleExtra(EXTRA_ARGUMENTS);
        mFragment.setArguments(arguments);

        return mFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_content_browser;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_goto_home) {
            if (mFragment != null) {
                mFragment.cancelAsyncTasks();
            }

            MenuActions.goToHome(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
