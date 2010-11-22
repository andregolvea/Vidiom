package au.com.infiniterecursion.roboticeye;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/*
 * RoboticEye Library Activity 
 * 
 * AUTHORS:
 * 
 * Andy Nicholson
 * 
 * 2010
 * Copyright Infinite Recursion Pty Ltd.
 * http://www.infiniterecursion.com.au
 */

public class LibraryActivity extends ListActivity implements RoboticEyeActivity {

	// Database
	DBUtils dbutils;
	private String[] video_absolutepath;
	private Integer[] video_ids;
	
	private boolean videos_available;

	private static final int MENU_ITEM_1 = Menu.FIRST;
	private static final int MENU_ITEM_2 = MENU_ITEM_1 + 1;
	private static final int MENU_ITEM_3 = MENU_ITEM_2 + 1;
	private static final int MENU_ITEM_4 = MENU_ITEM_3 + 1;

	private static final String TAG = "RoboticEye-Library";
	private PublishingUtils pu;

	private Handler handler;
	private String emailPreference;
	private SimpleCursorAdapter listAdapter;
	private Cursor libraryCursor;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());
		emailPreference = prefs.getString("emailPreference", null);

		
		dbutils = new DBUtils(getBaseContext());
		pu = new PublishingUtils(getResources(), dbutils);
		handler = new Handler();
	}

	public void onResume() {
		super.onResume();
		
		setContentView(R.layout.library_layout);
		
		makeCursorAndAdapter();

		registerForContextMenu(getListView());

		hideProgressIndicator();

	}

	private void makeCursorAndAdapter() {
		dbutils.genericWriteOpen();

		libraryCursor = dbutils.generic_write_db.query(
				DatabaseHelper.SDFILERECORD_TABLE_NAME, null, null, null, null,
				null, DatabaseHelper.SDFileRecord.DEFAULT_SORT_ORDER);
		//startManagingCursor(libraryCursor);

		if (libraryCursor.moveToFirst()) {
			ArrayList<Integer> video_ids_al = new ArrayList<Integer>();
			ArrayList<String> video_paths_al = new ArrayList<String>();

			do {
				long video_id = libraryCursor
						.getLong(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord._ID));
				video_ids_al.add((int) video_id);
				String video_path = libraryCursor
						.getString(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.FILEPATH));
				video_paths_al.add(video_path);

			} while (libraryCursor.moveToNext());

			video_ids = video_ids_al.toArray(new Integer[video_ids_al.size()]);
			video_absolutepath = video_paths_al
					.toArray(new String[video_paths_al.size()]);
			videos_available = true;

		} else {

			videos_available = false;

		}
		

		//Make Cursor Adapter

		String[] from = new String[] { DatabaseHelper.SDFileRecord.FILENAME,
				DatabaseHelper.SDFileRecord.LENGTH_SECS,
				DatabaseHelper.SDFileRecord.CREATED_DATETIME };
		int[] to = new int[] { android.R.id.text1, android.R.id.text2,
				R.id.text3 };
		listAdapter = new SimpleCursorAdapter(this,
				R.layout.library_list_item, libraryCursor, from, to);

		listAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			public boolean setViewValue(View view, Cursor cursor,
					int columnIndex) {
				//Transform the text3 specifically, from time in millis to text repr.
				if (columnIndex == cursor
						.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.CREATED_DATETIME)) {
					long time_in_mills = cursor.getLong(cursor
							.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.CREATED_DATETIME));
					TextView datetime = (TextView) view
							.findViewById(R.id.text3);
					datetime.setText(PublishingUtils.showDate(time_in_mills));
					return true;
				}
				return false;
			}
		});

		setListAdapter(listAdapter);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, " onDestroy ");
		libraryCursor.close();
		dbutils.close();
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, final int position,
			long id) {
		super.onListItemClick(l, v, position, id);
		
		//play this selection.
		String movieurl = video_absolutepath[(int) position];
		Log.d(TAG, " operation on " + movieurl);
		
		pu.launchVideoPlayer(this, movieurl);
	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {

		menu.add(0, MENU_ITEM_1, 0, R.string.library_menu_play);
		menu.add(0, MENU_ITEM_2, 0, R.string.library_menu_delete);
		menu.add(0, MENU_ITEM_3, 0, R.string.menu_publish_to_videobin);
		menu.add(0, MENU_ITEM_4, 0, R.string.menu_send_via_email);

	}

	public boolean onContextItemSelected(MenuItem item) {

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		Log.d(TAG, " got " + item.getItemId() + " at position " + info.position);
		
		
		if (!videos_available) {
			return true;
		}

		String movieurl = video_absolutepath[info.position];
		Integer movieid = video_ids[info.position];
		Log.d(TAG, " operation on " + movieurl + " id " + movieid.longValue());

		switch (item.getItemId()) {

		case MENU_ITEM_1:
			// play
			pu.launchVideoPlayer(this, movieurl);
			break;

		case MENU_ITEM_2:
			// delete

			// deleting files,
			if (!pu.deleteVideo(movieurl)) {
				Log.w(TAG, "Cant delete file " + movieurl);
				
			}
			// and removing DB records!
			if (dbutils.deleteSDFileRecord(movieid) == -1) {
				Log.w(TAG, "Cant delete record " + movieid);
			}
			
			//Refresh the list view
			runOnUiThread(new Runnable() {
			    public void run() {
			    	
			    	makeCursorAndAdapter();
					
					listAdapter.notifyDataSetChanged();

			    }
			});

			
			break;

		case MENU_ITEM_3:
			// publish to video bin
			pu.doPOSTtoVideoBin(this, handler, movieurl, emailPreference);
			break;

		case MENU_ITEM_4:
			// email
			pu.launchEmailIntentWithCurrentVideo(this, movieurl);
			break;

		}

		return true;

	}

	public void showProgressIndicator() {
		//
		findViewById(R.id.uploadprogresslibrary).setVisibility(View.VISIBLE);
	}

	public void hideProgressIndicator() {
		//
		findViewById(R.id.uploadprogresslibrary).setVisibility(View.INVISIBLE);
	}

}
