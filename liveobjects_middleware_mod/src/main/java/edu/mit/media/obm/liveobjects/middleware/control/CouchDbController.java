package edu.mit.media.obm.liveobjects.middleware.control;

import android.content.Context;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.android.AndroidContext;
import com.noveogroup.android.log.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implementation of DbController using couchbase-lite database
 * https://github.com/couchbase/couchbase-lite-android
 * @author Valerio Panzica La Manna <vpanzica@mit.edu>
 */
public class CouchDbController implements DbController{
    public static final String DB_NAME = "live-objects";

    private Manager mManager;
    private Database mDatabase;


    public CouchDbController(Context context) {
        createManager(context);
        createDb(DB_NAME);
    }

    private void createManager(Context context) {
        try {
            mManager = new Manager(new AndroidContext(context), Manager.DEFAULT_OPTIONS);
            Log.d("Manager created");
        } catch (IOException e) {
            Log.e("Cannot create manager object", e);
            return;
        }
    }

    private void createDb(String dbName) {
        try {
            mDatabase = mManager.getDatabase(dbName);
            Log.d("database created");
        } catch (CouchbaseLiteException e) {
            Log.e("Cannot get database");
            return;
        }
    }

    @Override
    public Map<String, Object> getProperties(String liveObjectId) {
        Document liveObjDocument = mDatabase.getDocument(liveObjectId);
        return liveObjDocument.getProperties();
    }

    @Override
    public Object getProperty(String liveObjectId, String key) {
        Document liveObjDocument = mDatabase.getDocument(liveObjectId);
        return liveObjDocument.getProperty(liveObjectId);
    }

    @Override
    public List<String> getLiveObjectsIds() {
        Query query = mDatabase.createAllDocumentsQuery();
        List<String> liveObjectsIds= new ArrayList<>();
        try {
            QueryEnumerator result = query.run();
            for (Iterator<QueryRow> it = result; it.hasNext(); ) {
                QueryRow row = it.next();
                liveObjectsIds.add(row.getDocumentId());
            }

        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        return liveObjectsIds;

    }

    @Override
    public List<Map<String, Object>> getAllLiveObjectsProperties() {
        Query query = mDatabase.createAllDocumentsQuery();
        List<Map<String, Object>> listOfLiveObjectsProperties = new ArrayList<>();
        try {
            QueryEnumerator result = query.run();
            for (Iterator<QueryRow> it = result; it.hasNext(); ) {
                QueryRow row = it.next();
                listOfLiveObjectsProperties.add(getProperties(row.getDocumentId()));
            }

        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }


        return listOfLiveObjectsProperties;
    }

    @Override
    public void putLiveObject(String liveObjectId, Map<String, Object> properties) {
        Document liveObjDocument = mDatabase.getDocument(liveObjectId);
        try {
            liveObjDocument.putProperties(properties);
        } catch (CouchbaseLiteException e) {
            Log.v("updating properties");
            updateProperties(liveObjectId, properties);
        }
    }

    private void updateProperties(String liveObjectId, final Map<String, Object> newProperties) {
        Document liveObjDocument = mDatabase.getDocument(liveObjectId);
        try {
            liveObjDocument.update(new Document.DocumentUpdater() {
                @Override
                public boolean update(UnsavedRevision newRevision) {
                    Log.v("update()");
                    Map<String, Object> properties = newRevision.getProperties();
                    properties.putAll(newProperties);
                    newRevision.setUserProperties(properties);

                    return true;
                }
            });
        } catch (CouchbaseLiteException e) {
            Log.e("not able to update the live object in the db ", e);
            e.printStackTrace();
        }
    }

    @Override
    public void putProperty(String liveObjectId, String key, Object value) {
       Map<String, Object> properties = new HashMap<>();
        properties.put(key, value);
        putLiveObject(liveObjectId, properties);
    }

    @Override
    public boolean isLiveObjectEmpty(String liveObjectId) {
        Document liveObjDocument = mDatabase.getDocument(liveObjectId);

        if (liveObjDocument.getCurrentRevision() == null) {
            return true;
        }

        // detected, but never connected live objects have empty properties
        Map<String, Object> properties = getProperties(liveObjectId);
        if (!properties.containsKey("contents")) {
            // if "title" is not included, the properties are incomplete
            // ToDo: should not rely on any assumption on a specific app
            return true;
        }

        return false;
    }


}
