package com.cloudant.sync.test;


import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Map;

import com.cloudant.sync.datastore.DocumentRevision;
import com.cloudant.sync.indexing.IndexManager;
import com.cloudant.sync.indexing.QueryBuilder;
import com.cloudant.sync.indexing.QueryResult;
import com.cloudant.sync.datastore.DatastoreManager;
import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DocumentBody;
import com.cloudant.sync.notifications.ReplicationCompleted;
import com.cloudant.sync.notifications.ReplicationErrored;
import com.cloudant.sync.replication.Replicator;
import com.cloudant.sync.replication.ReplicatorFactory;
import com.cloudant.sync.util.TypedDatastore;
import com.cloudant.sync.test.Task;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.Activity;
import android.content.Context;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class MainActivity extends Activity {
	
	private static final String USERNAME = "sabyapradhan";
	private static final String DATABASE = "test";
	private static final String API_KEY = "tedityfivistrandeapseste";
	private static final String API_PASSWORD = "cpg14cKVDCkxUOmYPtP6VMIG";
	
	private static final String DATASTORE_MANGER_DIR = "data";
	private static final String TASKS_DATASTORE_NAME = "tasks";
	
	private SecureRandom random = new SecureRandom();
	
	private TypedDatastore<Task> mTasksDatastore;
	private Datastore mDataStore; 
	
    private Replicator mPushReplicator;
    private Replicator mPullReplicator;
    
    private Handler mHandler;
    
    private boolean isPush = false; 
    
    private TextView mTextView; 

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Create a DatastoreManager using application internal storage path
		
        File path = getApplicationContext().getDir( DATASTORE_MANGER_DIR, Context.MODE_PRIVATE );
        DatastoreManager manager = new DatastoreManager(path.getAbsolutePath());

        mDataStore = manager.openDatastore(TASKS_DATASTORE_NAME);

		// A TypedDatastore handles object mapping from DBObject to another
        // class, saving us some code in this class for CRUD methods.
        mTasksDatastore = new TypedDatastore<Task>(Task.class, mDataStore);
        
        mHandler = new Handler(Looper.getMainLooper());
        
        mTextView = (TextView) findViewById(R.id.textView1);
        
        final Button addTaskButton = (Button) findViewById(R.id.button1);
        addTaskButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	addNewTask();
            }
        });
        
        final Button pullTasksButton = (Button) findViewById(R.id.button2);
        pullTasksButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	pullRecords();
            }
        });
        
        final Button cleanButton = (Button) findViewById(R.id.button3);
        cleanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	mTextView.setText("");
            }
        });
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
    private URI createServerURI()
            throws URISyntaxException {
        String host = USERNAME + ".cloudant.com";

        // We recommend always using HTTPS to talk to Cloudant.
        return new URI("https", API_KEY + ":" + API_PASSWORD, host, 443, "/" + DATABASE, null, null);
    }
    
    


   private String getTaskName() {
    	    return new BigInteger(130, random).toString(32);
    }
   
   private void addNewTask() {
	   String TaskName = getTaskName();
	   Task t = new Task( TaskName );
	   mTasksDatastore.createDocument(t);
	   
	   try {
		reloadReplicationSettings();
		startPushReplication();
		isPush = true; 
	} catch (URISyntaxException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	   
   }
   
	private void pullRecords() {
		try {
			reloadReplicationSettings();
			startPullReplication();
			isPush = false; 
			System.out.println(" XXXXXXXXXXXXXXXXXXXXXXX - PULLING");
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}
   
	private void queryRecords() {
		try {
			IndexManager indexManager = new IndexManager(mDataStore);
			// description
			indexManager.ensureIndexed("default", "type");

			System.out.println(" XXXXXXXXXXXXXXXXXXXXXXX - Query - 1");

			QueryBuilder query = new QueryBuilder();
			query.index("default").equalTo("com.cloudant.sync.example.task");

			System.out.println(" XXXXXXXXXXXXXXXXXXXXXXX - Query - 2");
			// Run the query
			QueryResult result = indexManager.query(query.build());

			System.out.println(" XXXXXXXXXXXXXXXXXXXXXXX - Query - 3");

			String value = "Desciptions of Records Pulled :" + "\n\n";

			long resultSize = result.size();
			int i = 0; 

			for (DocumentRevision revision : result) {
				// show data
				DocumentBody body = revision.getBody();
				Map<String, Object> map = body.asMap();
                ++i; 
				value = value + i + ". " + map.get("description") + "\n";
			}

			mTextView.setText("");
			mTextView.setText(value);
			Toast.makeText(getApplicationContext(),"Number of Records Pulled : " + resultSize, 10).show();
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
   
   /**
    * <p>Stops running replications.</p>
    *
    * <p>The stop() methods stops the replications asynchronously, see the
    * replicator docs for more information.</p>
    */
   private void stopAllReplications() {
       if (this.mPullReplicator != null) {
           this.mPullReplicator.stop();
       }
       if (this.mPushReplicator != null) {
           this.mPushReplicator.stop();
       }
   }

   /**
    * <p>Starts the configured push replication.</p>
    */
   private void startPushReplication() {
       if (this.mPushReplicator != null) {
           this.mPushReplicator.start();
       } else {
           throw new RuntimeException("Push replication not set up correctly");
       }
   }
   
   private void startPullReplication() {
       if (this.mPullReplicator != null) {
           this.mPullReplicator.start();
       } else {
           throw new RuntimeException("Push replication not set up correctly");
       }
   }
   
   @Subscribe
   public void complete(ReplicationCompleted rc) {
	   
	   if ( isPush ) {
		   System.out.println(" XXXXXXXXXXXXXXXXXXXXXXX - ReplicationCompleted - PUSH");
	   mHandler.post(new Runnable() {
           public void run() {
        	 	Toast.makeText(getApplicationContext(), "New Task Added", 10).show();
           }
       });
	   } else {
		   System.out.println(" XXXXXXXXXXXXXXXXXXXXXXX - ReplicationCompleted - PULL");
		   mHandler.post(new Runnable() {
	           public void run() {
	    		   queryRecords();
	           }
	       });
	   }
   }
   
   @Subscribe
   public void error(final ReplicationErrored re) {
       mHandler.post(new Runnable() {
           @Override
           public void run() {
        	   Toast.makeText(getApplicationContext(), re.errorInfo.getException().getMessage(), 10).show();
               }
       });
   }
   
   public void reloadReplicationSettings()
           throws URISyntaxException {

       // Stop running replications before reloading the replication
       // settings.
       // The stop() method instructs the replicator to stop ongoing
       // processes, and to stop making changes to the datastore. Therefore,
       // we don't clear the listeners because their complete() methods
       // still need to be called once the replications have stopped
       // for the UI to be updated correctly with any changes made before
       // the replication was stopped.
       this.stopAllReplications();

       // Set up the new replicator objects
       URI uri = createServerURI();

       mPushReplicator = ReplicatorFactory.oneway(mDataStore, uri);
       mPushReplicator.getEventBus().register(this);
       
       mPullReplicator = ReplicatorFactory.oneway(uri, mDataStore);
       mPullReplicator.getEventBus().register(this);
   } 

}
