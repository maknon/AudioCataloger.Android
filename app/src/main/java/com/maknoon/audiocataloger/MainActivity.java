package com.maknoon.audiocataloger;

import android.Manifest;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.tabs.TabLayout;
import androidx.appcompat.app.AppCompatDelegate;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.ShortDynamicLink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements Handler.Callback,
		SheekhListFragment.setOnPlayListener, SearchListFragment.setOnPlayListener,
		FeqhListFragment.setOnPlayListener, FavoriteListFragment.setOnPlayListener
{
	final private static String TAG = "MainActivity";

	static CharSequence[] sheekhNames;
	static int[] sheekhIds;
	static boolean[] sheekhSelected;

	static String sdPath;
	SharedPreferences mPrefs;

	static final int MSG_DB_INITIAT_DONE = 1;

	ProgressDialog dbInitiateDialog;

	Handler handler;

	// This flag should be set to true to enable VectorDrawable support for API < 21
	static
	{
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
	}

	@Override
	protected void attachBaseContext(Context base)
	{
		super.attachBaseContext(ContextWrapper.wrap(base, new Locale("ar")));
	}

	@Override
	public void onAttachFragment(@NonNull Fragment fragment)
	{
		if (fragment instanceof SheekhListFragment)
		{
			final SheekhListFragment sheekhListFragment = (SheekhListFragment) fragment;
			sheekhListFragment.setOnPlayListener(this);
		}
		else if (fragment instanceof SearchListFragment)
		{
			final SearchListFragment searchListFragment = (SearchListFragment) fragment;
			searchListFragment.setOnPlayListener(this);
		}
		else if (fragment instanceof FavoriteListFragment)
		{
			final FavoriteListFragment favoriteListFragment = (FavoriteListFragment) fragment;
			favoriteListFragment.setOnPlayListener(this);
		}
		else if (fragment instanceof FeqhListFragment)
		{
			final FeqhListFragment feqhListFragment = (FeqhListFragment) fragment;
			feqhListFragment.setOnPlayListener(this);
		}
	}

	@Override
	public void play(final int offset, final int duration, final String url, final String title, final String subTitle)
	{
		final MediaControllerCompat mediaControllerCompat = MediaControllerCompat.getMediaController(this);
		if (mediaControllerCompat != null)
		{
			final Bundle bundle = new Bundle();
			bundle.putString("url", url);
			bundle.putInt("offset", offset);
			bundle.putInt("duration", duration);
			bundle.putString("title", title);
			bundle.putString("subTitle", subTitle);
			mediaControllerCompat.sendCommand("play", bundle, null);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.v(TAG, "onCreate");

		getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // force RTL

		super.onCreate(savedInstanceState);

		mPrefs = getSharedPreferences("setting", Context.MODE_PRIVATE);

		File sdPath_tmp;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
			sdPath_tmp = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); // replace DIRECTORY_MUSIC with DIRECTORY_DOWNLOADS since it is the first one causing crashing in some devices
		else
		{
			if (Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
				sdPath_tmp = getExternalFilesDir(null); // return null for the first App startup in api 30, maybe a bug. ask user to restart App
			else
				sdPath_tmp = getFilesDir();
		}

		sdPath = mPrefs.getString("sdPath", (sdPath_tmp == null) ? null : (sdPath_tmp.getAbsolutePath() + "/"));

		// Version 8.0
		if (sdPath == null)
			Toast.makeText(this, "App does not have any place to save files! Restart App", Toast.LENGTH_LONG).show();

		// Register here to be running all the time while the app is running, to avoid unregister it if app is swapped by other application. DownloadManager will continue in all cases and the receive broadcast will be lost and the file will not be renamed from *.m4a.part -> *.m4a
		//LocalBroadcastManager.getInstance(this).registerReceiver(); will not work
		registerReceiver(DownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
		LocalBroadcastManager.getInstance(this).registerReceiver(saveFavoriteReceiver, new IntentFilter(MediaService.Broadcast_saveFavorite));

		// Version 7, to stop playing if call arrives. Update: remove READ_PHONE_STATE and replace it it with LISTEN_CALL_STATE which does not need permissions
		//if(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
		//	ActivityCompat.requestPermissions((Activity)context, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_READ_PHONE_STATE_PERMISSION);

		boolean dbThreadLive = false;
		final Set<Thread> threads = Thread.getAllStackTraces().keySet();
		for (Thread t : threads)
		{
			if (t.getName().equals("DBThread"))
			{
				dbThreadLive = true;
				break;
			}
		}

		if (dbThreadLive)
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

			dbInitiateDialog = new ProgressDialog(this);
			dbInitiateDialog.setMessage("بدأ تجهيز البرنامج للمرة الأولى" + "\n" + "قد يستغرق بضع دقائق");
			dbInitiateDialog.setCancelable(false);
			dbInitiateDialog.show();
		}
		else
		{
			final int oldVer = mPrefs.getInt("version", 0);
			if (oldVer != BuildConfig.VERSION_CODE) // New installation or upgrade, override the whole DB
			{
				// This is working better than WakeLock
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

				dbInitiateDialog = new ProgressDialog(this);
				dbInitiateDialog.setMessage("يتم تجهيز البرنامج للمرة الأولى" + "\n" + "قد يستغرق 3 دقائق");
				dbInitiateDialog.setCancelable(false);
				dbInitiateDialog.show();

				handler = new Handler(this);

				// To create or upgrade the DB for the first time
				final Thread dbThread = new Thread(new DBThread(this, handler));
				dbThread.setName("DBThread");
				dbThread.start();
			}
			else
			{
				setUI();
			}
		}
	}

	class DBThread implements Runnable
	{
		Context ctx;
		Handler handler;
		DBThread(Context ctx, Handler handler)
		{
			this.ctx = ctx;
			this.handler = handler;
		}

		public void run()
		{
			// Clean older version if available from corrupted previous startup
			deleteDatabase(DBHelper.DB_NAME);
			deleteDatabase(DBHelper.DB_ATTACH_NAME);

			copyDataBase(DBHelper.DB_NAME); // TODO: delete db in the asset folder after replacing the app db with it
			copyDataBase(DBHelper.DB_ATTACH_NAME); // Version 5, divide the DB so that we have Contents table in a separate DB. this allows to delete the DB after creating FTS3 and save 100mb. also it increase the speed by 50% !

			final DBHelper dbHelper = new DBHelper(ctx);
			final SQLiteDatabase db = dbHelper.getWritableDatabase();

			// Improve the performance by 15% only
			final Cursor mCursor1 = db.rawQuery("PRAGMA cache_size = 4096", null);
			//final Cursor mCursor2 = db.rawQuery("PRAGMA locking_mode = EXCLUSIVE", null);
			//final Cursor mCursor3 = db.rawQuery("PRAGMA synchronous = OFF", null);
			//final Cursor mCursor4 = db.rawQuery("PRAGMA journal_mode = OFF", null);

			mCursor1.close();
			//mCursor2.close();
			//mCursor3.close();
			//mCursor4.close();

			db.execSQL(String.format("ATTACH DATABASE '%s' AS db", getDatabasePath(DBHelper.DB_ATTACH_NAME).getAbsolutePath()));

			//db.execSQL("CREATE VIRTUAL TABLE Contents_FTS USING fts4(matchinfo=fts3, Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Book_id INTEGER, Offset TEXT, Duration TEXT, Category_id TEXT, Line TEXT, Tafreeg TEXT)"); // Android 4.2 is not supporting FTS4 notindexed only 5.0 is supporting this. TODO: Please add once we start using 5.0 ..... notindexed=Seq, notindexed=Book_id, notindexed=Offset, notindexed=Duration, notindexed=Tafreeg
			db.execSQL("CREATE VIRTUAL TABLE Contents_FTS USING fts4 (Code, Seq, Sheekh_id, Book_id, \"Offset\", Duration, Category_id, Line, Tafreeg)"); // Android 4.2 is not supporting FTS4 notindexed only 5.0 is supporting this. TODO: Please add once we start using 5.0 ..... notindexed=Seq, notindexed=Book_id, notindexed=Offset, notindexed=Duration, notindexed=Tafreeg

			//db.beginTransaction(); // Did not improve the performance
			db.execSQL("INSERT INTO Contents_FTS SELECT * FROM db.Contents");
			//db.setTransactionSuccessful();
			//db.endTransaction();

			//db.execSQL("DROP TABLE Contents"); // No need for this since we cannot reclaim the space, VACUUM is taking forever.

			db.execSQL("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id)");
			db.execSQL("CREATE INDEX Chapters_Book_id ON Chapters(Book_id)");
			db.execSQL("CREATE INDEX Chapters_Code ON Chapters(Code)");
			db.execSQL("CREATE INDEX Category_Category_parent ON Category(Category_parent)");
			db.execSQL("CREATE INDEX Category_Category_id ON Category(Category_id)");

			//db.execSQL("VACUUM FULL"); take very long time 30 min without much improvement

			//db.close(); // You cannot close DB inside onCreate(), otherwise it will be closed totally and cannot reopen
			deleteDatabase(DBHelper.DB_ATTACH_NAME); // Version 5, Much better than VACUUM
			db.close();

			mPrefs.edit().putInt("version", BuildConfig.VERSION_CODE).apply();

			if(handler != null)
				Message.obtain(handler, MSG_DB_INITIAT_DONE, "").sendToTarget();
		}
	}

	private void setUI()
	{
		setContentView(R.layout.activity_main);

		final ActionBar ab = getSupportActionBar();
		if (ab != null)
			ab.setTitle(R.string.app_name);

		final SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), getLifecycle());
		final ViewPager2 mViewPager = findViewById(R.id.viewpager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		/* Version 5
		 This solves many issues:
		 - all pages are loaded once, so no reload is happening all the time
		 - we will save the current status of each page i.e. we will not loose the content or where we are
		 - will prevent NullPointerException when calling saveFavorite() if we didn't visit the FavoriteList at all (i.e. it will be null at the begining until you visit it once).
		 ((FavoriteListFragment)getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":" + 3)).displayFavoriteList();
		*/
		mViewPager.setOffscreenPageLimit(mSectionsPagerAdapter.getItemCount());

		final TabLayout tabLayout = findViewById(R.id.tabs);
		//tabLayout.setupWithViewPager(mViewPager);

		// To fill the space in the toolbar (equal width tabs)
		tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
		tabLayout.setTabMode(TabLayout.MODE_FIXED);
		tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener()
		{
			@Override
			public void onTabSelected(TabLayout.Tab tab)
			{
			}

			@Override
			public void onTabUnselected(TabLayout.Tab tab)
			{
			}

			@Override
			public void onTabReselected(TabLayout.Tab tab)
			{
				final int index = tab.getPosition();
				switch (index)
				{
					case 0:
						((SheekhListFragment) getSupportFragmentManager().getFragments().get(0)).displaySheekh();
						break;
					case 1:
						((FeqhListFragment) getSupportFragmentManager().getFragments().get(1)).displayFeqhChild(0, ""); // Empty means root
						break;
				}
			}
		});

		new TabLayoutMediator(tabLayout, mViewPager, new TabLayoutMediator.TabConfigurationStrategy()
		{
			@Override
			public void onConfigureTab(@NonNull TabLayout.Tab tab, int position)
			{
				if (position == 0)
					tab.setText(R.string.title_section1);
				else
					if (position == 1)
						tab.setText(R.string.title_section2);
					else
						if (position == 2)
							tab.setIcon(R.drawable.sharp_search_24);
						else
							if (position == 3)
								tab.setIcon(R.drawable.outline_bookmark_border_24);
			}
		}).attach();

		mViewPager.setCurrentItem(0, true);

		final DBHelper mDbHelper = new DBHelper(this);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor = db.rawQuery("SELECT * FROM Sheekh ORDER BY Sheekh_id", null);

		sheekhNames = new CharSequence[mCursor.getCount()];
		sheekhIds = new int[mCursor.getCount()];
		sheekhSelected = new boolean[mCursor.getCount()];

		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < mCursor.getCount(); i++)
			{
				final String sheekh_name = mCursor.getString(mCursor.getColumnIndexOrThrow("Sheekh_name"));
				final int sheekh_id = mCursor.getInt(mCursor.getColumnIndexOrThrow("Sheekh_id"));
				sheekhNames[i] = sheekh_name;
				sheekhIds[i] = sheekh_id;
				sheekhSelected[i] = true;
				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

		initMediaController();
	}

	/**
	 * Copies your database from your local assets-folder to the just created empty database in the
	 * system folder, from where it can be accessed and handled.
	 */
	private void copyDataBase(final String db)
	{
		final String path = getDatabasePath(db).getAbsolutePath();

		final File f = new File(path);
		final boolean cont = f.getParentFile().mkdirs();

		//if(cont) // it can be created previously
		{
			try
			{
				// Open your local db as the input stream
				final InputStream in = getAssets().open(db);

				// Open the empty db as the output stream
				final OutputStream out = new FileOutputStream(path);

				//transfer bytes from the inputfile to the outputfile
				byte[] buffer = new byte[1024 * 3];
				int length;
				while ((length = in.read(buffer)) > 0)
					out.write(buffer, 0, length);

				out.close();
				in.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	static String SQL_Combination = null;
	static boolean wholeDB = true;

	//AlertDialog directoryDialog;
	//ListView directoryList;

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.

		switch (item.getItemId())
		{
			case R.id.search_range:
			{
				final AlertDialog.Builder ad = new AlertDialog.Builder(this);
				ad.setTitle("نطاق البحث");
				ad.setMultiChoiceItems(sheekhNames, sheekhSelected, new DialogInterface.OnMultiChoiceClickListener()
				{
					public void onClick(DialogInterface dialog, int clicked, boolean selected)
					{
						//Log.i("maknoon", sheekhNames[clicked] + " selected: " + selected);
					}
				});

				ad.setPositiveButton("إلغاء الكل", new DialogInterface.OnClickListener()
				{
					// This will not override the behavior of this button (close after click)
					public void onClick(DialogInterface dialog, int id)
					{
					}
				});

				ad.setNegativeButton("تحديد الكل", new DialogInterface.OnClickListener()
				{
					// This will not override the behavior of this button (close after click)
					public void onClick(DialogInterface dialog, int id)
					{
					}
				});

				ad.setNeutralButton("إغلاق", new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int id)
					{
						//for (int i = 0; i < sheekhNames.length; i++)
						//	Log.i("maknoon", sheekhNames[i] + " selected: " + sheekhSelected[i]);

						SQL_Combination = null;
						wholeDB = true;

						for (int i = 0; i < sheekhIds.length; i++)
						{
							if (sheekhSelected[i])
							{
								if (SQL_Combination != null)
									SQL_Combination = SQL_Combination + " OR Sheekh_id:" + sheekhIds[i];
								else
									SQL_Combination = "Sheekh_id:" + sheekhIds[i];
							}
							else
								wholeDB = false;
						}

						dialog.cancel();
					}
				});

				final AlertDialog d = ad.create();
				d.show();

				final ListView lv = d.getListView();
				lv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // manually done since the default RTL is not working for this list ?

				d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						final ListView lv = d.getListView();
						for (int i = 0; i < sheekhNames.length; i++)
						{
							lv.setItemChecked(i, true);
							sheekhSelected[i] = true;
							//lv.performItemClick(v, i, 0);
						}
						//d.dismiss();
						//else dialog stays open. Make sure you have an obvious way to close the dialog especially if you set cancellable to false.
					}
				});
				d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						final ListView lv = d.getListView();
						for (int i = 0; i < sheekhNames.length; i++)
						{
							lv.setItemChecked(i, false);
							sheekhSelected[i] = false;
							//lv.performItemClick(v, i, 0);
						}
					}
				});
			}
			return true;

			case R.id.player:
				if(exoPlayerView != null)
					exoPlayerView.show();
				return true;

			// Version 7.2
			case R.id.audios_path:
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
				{
					if (sdPath == null)
						Toast.makeText(this, "App does not have any place to save files! Restart App", Toast.LENGTH_LONG).show();
					else
					{
						final Intent intent = new Intent(this, AppRecordingPath.class);
						intent.putExtra("sdPath", sdPath);
						startActivityForResult(intent, ACTIVITY_RECORDING_LOCATION);
					}
				}
				else
				{
					// Should we show an explanation?
					//if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
						//System.out.println("shouldShowRequestPermissionRationale");
					//else
						// No explanation needed, we can request the permission.
						ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, DIRECTORY_CHOOSER_PERMISSION);
				}

				/* Another solution but not look nice
				final AlertDialog.Builder ad = new AlertDialog.Builder(context);
				ad.setTitle(sdPath);

				final String[] fileList = refreshDirectoryList(new File(sdPath));

				ad.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, fileList)
				{
					@Override
					public View getView(int pos, View view, ViewGroup parent)
					{
						view = super.getView(pos, view, parent);
						((TextView) view).setSingleLine(true);
						return view;
					}
				}, null);

				ad.setPositiveButton("حفظ", new DialogInterface.OnClickListener()
				{
					// This will not override the behavior of this button (close after click)
					public void onClick(DialogInterface dialog, int id)
					{
						final ListView lv = directoryDialog.getListView();
						for (int i = 0; i < sheekhNames.length; i++)
						{
							lv.setItemChecked(i, false);
							sheekhSelected[i] = false;
							//lv.performItemClick(v, i, 0);
						}

						sdPath = sdPath_tmp.toString()+"/";

						SharedPreferences.Editor mEditor = mPrefs.edit();
						mEditor.putString("sdPath", sdPath).apply();

						Toast.makeText(context, sdPath, Toast.LENGTH_LONG).show();
						directoryDialog.dismiss();
					}
				});

				ad.setNegativeButton("إلغاء", new DialogInterface.OnClickListener()
				{
					// This will not override the behavior of this button (close after click)
					public void onClick(DialogInterface dialog, int id)
					{
						directoryDialog.dismiss();
					}
				});

				directoryDialog = ad.create();
				directoryDialog.show();
				//directoryDialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT); it should be after show()

				directoryList = directoryDialog.getListView();
				directoryList.setOnItemClickListener(new AdapterView.OnItemClickListener()
				{
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int which, long id)
					{
						String fileChosen = (String) directoryList.getItemAtPosition(which);
						File chosenFile = getChosenFile(fileChosen);
						if(chosenFile.isDirectory())
						{
							final String[] fileList = refreshDirectoryList(chosenFile);

							// refresh the user interface
							directoryDialog.setTitle(sdPath_tmp.getPath());
							directoryList.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, fileList)
							{
								@Override
								public View getView(int pos, View view, ViewGroup parent)
								{
									view = super.getView(pos, view, parent);
									((TextView) view).setSingleLine(true);
									return view;
								}
							});
						}
					}
				});
				*/
				return true;

			case R.id.support:
				final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.maknoon.com/community/threads/%D8%A8%D8%B1%D9%86%D8%A7%D9%85%D8%AC-%D9%85%D9%81%D9%87%D8%B1%D8%B3-%D8%A7%D9%84%D9%85%D8%AD%D8%A7%D8%B6%D8%B1%D8%A7%D8%AA-%D8%A3%D9%86%D8%AF%D8%B1%D9%88%D9%8A%D8%AF.120/post-154"));
				startActivity(browserIntent);
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

    /*
	private static final String PARENT_DIR = "..";
	private File sdPath_tmp;

	// Sort, filter and display the files for the given path.
	private String[] refreshDirectoryList(File path)
	{
		sdPath_tmp = path;
		if(path.exists())
		{
			File[] dirs = path.listFiles(new FileFilter()
			{
				@Override
				public boolean accept(File file)
				{
					return (file.isDirectory() && file.canRead());
				}
			});

			// convert to an array
			int i = 0;
			String[] fileList;
			if (path.getParentFile() == null)
				fileList = new String[dirs.length];
			else
			{
				fileList = new String[dirs.length + 1];
				fileList[i++] = PARENT_DIR;
			}

			Arrays.sort(dirs);
			for (File dir : dirs)
				fileList[i++] = dir.getName();

			return fileList;
		}
		return null;
	}

	// Convert a relative filename into an actual File object.
	private File getChosenFile(String fileChosen)
	{
		if (fileChosen.equals(PARENT_DIR))
			return sdPath_tmp.getParentFile();
		else
			return new File(sdPath_tmp, fileChosen);
	}
	*/

	@Override
	protected void onStart()
	{
		super.onStart();
		Log.v(TAG, "onStart");

		if (mediaBrowserCompat == null)
			Log.e(TAG, "mediaBrowserCompat is null !!");
		else
		{
			// try-catch is used to avoid the strange error while resuming the app and starting at the same time
			// java.lang.IllegalStateException: connect() called while neither disconnecting nor disconnected (state=CONNECT_STATE_CONNECTING)
			// https://github.com/android/uamp/issues/251
			try
			{
				if (!mediaBrowserCompat.isConnected())
					mediaBrowserCompat.connect();
			}
			catch (IllegalStateException e)
			{
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onDestroy()
	{
		Log.v(TAG, "onDestroy");

		if (handler != null)
		{
			handler.removeCallbacksAndMessages(null);
			handler = null;
		}

		unregisterReceiver(DownloadReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(saveFavoriteReceiver);

		super.onDestroy();
	}

	/*
	// Broadcast receiver to detect incoming call
	private BroadcastReceiver onCallReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context c, Intent i)
		{
			Log.v("maknoon:Main", "onCallReceiver");
			//playerSrv.pause(); // Works only in Android 5.0 and below
		}
	};
	*/

	private BroadcastReceiver saveFavoriteReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context c, Intent i)
		{
			Log.v(TAG, "saveFavoriteReceiver");
			saveFavorite();
		}
	};

	private BroadcastReceiver DownloadReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context c, Intent i)
		{
			final long receivedID = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
			final DownloadManager mgr = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
			if(mgr != null)
			{
				final DownloadManager.Query query = new DownloadManager.Query();
				query.setFilterById(receivedID);
				final Cursor cur = mgr.query(query);
				if (cur.moveToFirst())
				{
					final int uri = cur.getColumnIndex(DownloadManager.COLUMN_URI);
					Log.v(TAG, "Download uri: " + cur.getString(uri));

					final int uriIndex = cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
					final String uriString = cur.getString(uriIndex);
					Log.v(TAG, "COLUMN_LOCAL_URI: " + uriString);

					final int index = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
					if (cur.getInt(index) == DownloadManager.STATUS_SUCCESSFUL)
					{
						//final int uriIndex = cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
						//final String uriString  = cur.getString(uriIndex);
						//Log.v(TAG, "Download success: " + uriString);

						final File f = new File(Uri.parse(uriString).getPath());
						f.renameTo(new File(Uri.parse(uriString.substring(0, uriString.length() - 5)).getPath())); // remove last 5 characters ".part"
					}
					else
					{
						final int reason = cur.getColumnIndex(DownloadManager.COLUMN_REASON);
						Log.v(TAG, "Download Failed: " + cur.getInt(reason));
					}
				}
				cur.close();
				((SheekhListFragment) getSupportFragmentManager().getFragments().get(0)).refresh(); // Refresh the view to show the complete icon for this chapter
			}
		}
	};

	private MediaBrowserCompat mediaBrowserCompat;
	private final MediaBrowserCompat.ConnectionCallback mediaBrowserCompatConnectionCallback = new MediaBrowserCompat.ConnectionCallback()
	{
		@Override
		public void onConnected()
		{
			super.onConnected();

			final MediaControllerCompat mediaControllerCompat = new MediaControllerCompat(MainActivity.this, mediaBrowserCompat.getSessionToken());

			// Register a Callback to stay in sync
			mediaControllerCompat.registerCallback(mediaControllerCompatCallback);

			mediaControllerCompatCallback.onMetadataChanged(mediaControllerCompat.getMetadata());
			mediaControllerCompatCallback.onPlaybackStateChanged(mediaControllerCompat.getPlaybackState());

			MediaControllerCompat.setMediaController(MainActivity.this, mediaControllerCompat);

			// Finish building the UI
			buildTransportControls();
		}

		@Override
		public void onConnectionSuspended()
		{
			// The Service has crashed. Disable transport controls until it automatically reconnects
		}

		@Override
		public void onConnectionFailed()
		{
			// The Service has refused our connection
		}
	};

	PlayerControlView exoPlayerView;
	void buildTransportControls()
	{
		if(MediaService.mediaPlayer != null)
		{
			exoPlayerView = findViewById(R.id.playerView);
			exoPlayerView.setPlayer(MediaService.mediaPlayer);
		}

		final AppCompatImageButton saveFavorite = findViewById(R.id.saveFavorite);

		// Attach a listener to the button
		saveFavorite.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				saveFavorite();
			}
		});
	}

	private final MediaControllerCompat.Callback mediaControllerCompatCallback = new MediaControllerCompat.Callback()
	{
		@Override
		public void onPlaybackStateChanged(PlaybackStateCompat state)
		{
			super.onPlaybackStateChanged(state);

			if (state == null)
				return;

			switch (state.getState())
			{
				case PlaybackStateCompat.STATE_PLAYING:
					if(exoPlayerView != null)
						exoPlayerView.show();
					break;
				case PlaybackStateCompat.STATE_PAUSED:
					break;
				case PlaybackStateCompat.STATE_BUFFERING:
					break;
				case PlaybackStateCompat.STATE_CONNECTING:
					break;
				case PlaybackStateCompat.STATE_ERROR:
					break;
				case PlaybackStateCompat.STATE_FAST_FORWARDING:
					break;
				case PlaybackStateCompat.STATE_NONE:
					break;
				case PlaybackStateCompat.STATE_REWINDING:
					break;
				case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
					break;
				case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
					break;
				case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
					break;
				case PlaybackStateCompat.STATE_STOPPED:
					break;
			}
		}

		@Override
		public void onMetadataChanged(MediaMetadataCompat metadata)
		{
		}
	};

	private void initMediaController()
	{
		if(mediaBrowserCompat == null)
		{
			mediaBrowserCompat = new MediaBrowserCompat(this,
					new ComponentName(this, MediaService.class),
					mediaBrowserCompatConnectionCallback, getIntent().getExtras());
		}
	}

	// Version 7
	@Override
	public void onBackPressed()
	{
		Log.v(TAG, "onBackPressed");
		final TabLayout tabLayout = findViewById(R.id.tabs);

		int index = tabLayout.getSelectedTabPosition();
		Log.v(TAG, "tabLayout: " + index);

		final boolean backable;

		switch (index)
		{
			case 0:
				//backable = ((SheekhListFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.viewpager + ":" + index)).back();
				backable = ((SheekhListFragment) getSupportFragmentManager().getFragments().get(0)).back();
				break;
			case 1:
				//backable = ((FeqhListFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.viewpager + ":" + index)).back();
				backable = ((FeqhListFragment) getSupportFragmentManager().getFragments().get(1)).back();
				break;
			default:
				backable = false;
		}

		if (!backable)
			super.onBackPressed();
	}

	public static String Urlshortener_firebase(final String URL)
	{
		try
		{
			final Task<ShortDynamicLink> shortLinkTask = FirebaseDynamicLinks.getInstance().createDynamicLink()
					.setLink(Uri.parse(URL))
					.setDomainUriPrefix("https://montaqa.com")
					// Open links with this app on Android
					//.setAndroidParameters(new DynamicLink.AndroidParameters.Builder().build())
					// Open links with com.example.ios on iOS
					//.setIosParameters(new DynamicLink.IosParameters.Builder("com.example.ios").build())
					.buildShortDynamicLink(ShortDynamicLink.Suffix.SHORT);
					/* TODO: this is the best implementation to not wait for the result
					.addOnCompleteListener(this, new OnCompleteListener<ShortDynamicLink>()
					{
						@Override
						public void onComplete(@NonNull Task<ShortDynamicLink> task)
						{
							if (task.isSuccessful())
							{
								// Short link created
								Uri shortLink = task.getResult().getShortLink();
								Uri flowchartLink = task.getResult().getPreviewLink();
							}
							else
							{
								// Error
							}
						}
					});
			 		*/

			//final ShortDynamicLink authResult = Tasks.await(shortLinkTask, 1500, TimeUnit.MILLISECONDS);
			final ShortDynamicLink authResult = Tasks.await(shortLinkTask);
			final Uri shortLink = authResult.getShortLink();
			//final Uri flowchartLink = authResult.getPreviewLink();

			return shortLink != null ? shortLink.toString() : URL;
		}
		catch (ExecutionException | InterruptedException e)
		{
			Log.e(TAG, e.getMessage(), e);
			return URL;
		}
	}

	@Override
	protected void onPause()
	{
		Log.v(TAG, "onPause");
		super.onPause();

		//unregisterReceiver(onCallReceiver);
		//unregisterReceiver(DownloadReceiver);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Log.v(TAG, "onResume");
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		//registerReceiver(onCallReceiver, new IntentFilter("android.intent.action.PHONE_STATE"));
		//registerReceiver(DownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
	}

	@Override
	protected void onStop()
	{
		Log.v(TAG, "onStop");
		//pause(); // Version 7.2, removed it again after introducing it in v7.0
		super.onStop();

		if (mediaBrowserCompat != null && mediaBrowserCompat.isConnected())
			mediaBrowserCompat.disconnect();

		/* This is onDestroy()
		if (mediaControllerCompat != null)
		{
			//if (MediaControllerCompat.getMediaController(this).getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING)
			if (mediaControllerCompat.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING)
				mediaControllerCompat.getTransportControls().stop();

			mediaControllerCompat.unregisterCallback(mediaControllerCompatCallback);
			mediaControllerCompat = null;
		}
		*/

		if (MediaControllerCompat.getMediaController(this) != null)
			MediaControllerCompat.getMediaController(this).unregisterCallback(mediaControllerCompatCallback);
	}

	protected void saveFavorite()
	{
		Log.v(TAG, "saveFavorite");

		final int pbState = MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState().getState();
		if (mediaBrowserCompat != null && mediaBrowserCompat.isConnected() && MediaService.mediaPlayer != null && (pbState == PlaybackStateCompat.STATE_PLAYING || pbState == PlaybackStateCompat.STATE_PAUSED))
		{
			/* Not working, http://stackoverflow.com/questions/32157862/set-add-in-android-sharedpreferences-is-not-working-as-expected
			final SharedPreferences favoriteList = context.getSharedPreferences("favoriteList", MODE_PRIVATE);
			final SharedPreferences currentChapter = context.getSharedPreferences("currentChapter", MODE_PRIVATE);

			// we must not modify the set instance returned by getStringSet(), hence we need to create a new instance. Check API
			final Set<String> reference = new HashSet<>(favoriteList.getStringSet("reference", new HashSet<String>()));
			final Set<String> path = new HashSet<>(favoriteList.getStringSet("path", new HashSet<String>()));
			final Set<String> fileName = new HashSet<>(favoriteList.getStringSet("fileName", new HashSet<String>()));
			final Set<String> offset = new HashSet<>(favoriteList.getStringSet("offset", new HashSet<String>()));

			Log.v("maknoon:Main", "saveFavorite ref: " + reference.size());

			/*
			final Set<String> r = favoriteList.getStringSet("reference", new HashSet<String>());
			final Set<String> p = favoriteList.getStringSet("path", new HashSet<String>());
			final Set<String> f = favoriteList.getStringSet("fileName", new HashSet<String>());
			final Set<String> o = favoriteList.getStringSet("offset", new HashSet<String>());

			// we must not modify the set instance returned by getStringSet(), hence we need to create a new instance. Check API
			final Set reference = ((Set)((HashSet) r).clone());
			final Set path = ((Set)((HashSet) p).clone());
			final Set fileName = ((Set)((HashSet) f).clone());
			final Set offset = ((Set)((HashSet) o).clone());
			*/

			/*
			Log.v("maknoon:Main", "saveFavorite: " + currentChapter.getString("reference", null) + ":::"+currentChapter.getString("fileName", null)+":::"+currentChapter.getString("path", null));

			reference.add(currentChapter.getString("reference", null));
			path.add(currentChapter.getString("path", null));
			fileName.add(currentChapter.getString("fileName", null));
			offset.add(String.valueOf(playerSrv.getPosn()));

			final SharedPreferences.Editor editor = favoriteList.edit();
			editor.clear();
			editor.putStringSet("reference", reference);
			editor.putStringSet("path", path);
			editor.putStringSet("fileName", fileName);
			editor.putStringSet("offset", offset);
			editor.apply();

			Log.v("maknoon:Main", "saveFavorite: " + reference.size());
			*/

			final SharedPreferences currentChapter = getSharedPreferences("currentChapter", MODE_PRIVATE);
			final ContentValues values = new ContentValues();

			values.put("Reference", currentChapter.getString("reference", null));
			values.put("path", currentChapter.getString("path", null));
			values.put("FileName", currentChapter.getString("fileName", null));
			values.put("Offset", MediaService.mediaPlayer.getCurrentPosition());

			final DBHelper mDbHelper = new DBHelper(this);
			final SQLiteDatabase db = mDbHelper.getWritableDatabase();
			db.insert("Favorite", null, values);
			db.close();

			((FavoriteListFragment) getSupportFragmentManager().getFragments().get(3)).displayFavoriteList();
		}
	}

	static void setCurrentChapter(String reference, String fileName, String path, Context context)
	{
		final SharedPreferences currentChapter = context.getSharedPreferences("currentChapter", MODE_PRIVATE);
		final SharedPreferences.Editor editor = currentChapter.edit();
		editor.putString("reference", reference);
		editor.putString("path", path);
		editor.putString("fileName", fileName);
		editor.apply();
	}


	public static String toURL(String path, String fileName, boolean shortUrl)
	{
		/*
		// Can be replaced by Uri.Builder
		String test = index.path+'\\'+index.fileName;
		try
		{
			// Replace only the arabic characters. space should be %20 and / should be the same. Z is not used in any path
			test = test.replace('\\', 'Z');
			test = java.net.URLEncoder.encode(test, "UTF-8");
			test = test.replace('Z', '/');
			test = test.replaceAll("\\+", "%20");
		}
		catch(Exception e){e.printStackTrace();}
		String url = "http://www.maknoon.com/download/audios/"+test + ".rm";
		*/

		/*
		String url = "http://www.maknoon.com/download/audios/" + index.path + '/' + index.fileName + ".rm";
		url = url.replace('\\', '/').replaceAll(" ", "%20");
		*/

		final Uri.Builder builder = new Uri.Builder();
		builder.scheme("http")
				.authority("www.maknoon.com")
				.appendPath("audios.m4a");

		for (String token : path.split("/"))
			builder.appendPath(token);

		builder.appendPath(fileName + ".m4a");

		/*
		Uri trackUri = ContentUris.withAppendedId(
				android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
				currSong);
				*/

		// Version 7
		if (shortUrl)
			return Urlshortener_firebase(builder.build().toString());
		else
			return builder.build().toString();
	}

	public static String toURL(String path, String fileName, int seq, boolean shortUrl)
	{
		final Uri.Builder builder = new Uri.Builder();
		builder.scheme("http")
				.authority("www.maknoon.com")
				.appendPath("audios.m4a.parts");

		for (String token : path.split("/"))
			builder.appendPath(token);

		builder.appendPath(fileName);
		builder.appendPath(seq + ".m4a");

		if (shortUrl) // Version 7
			return Urlshortener_firebase(builder.build().toString());
		else
			return builder.build().toString();
	}

	public static String toURL_File(final String path, final String fileName)
	{
		if (sdPath == null)
			Log.e(TAG,"App does not have any place to save files! Restart App");
		else
		//if (isSDPresent)
		{
			final File f = new File(sdPath + path + "/" + fileName + ".m4a");
			if (f.exists())
				return f.toString();
		}

		return toURL(path, fileName, false);
	}

	public static Uri toUri(String path)
	{
		final Uri.Builder builder = new Uri.Builder();
		builder.scheme("http")
				.authority("www.maknoon.com")
				.appendPath("audios.m4a");

		for (String token : path.split("/"))
			builder.appendPath(token);

		return builder.build();
	}

	private static final int DOWNLOAD_PERMISSION = 1;
	private static final int DIRECTORY_CHOOSER_PERMISSION = 2;
	private static final int ACTIVITY_RECORDING_LOCATION = 3;

	// Store the last request to execute it from onRequestPermissionsResult() once permission is granted
	static DownloadManager.Request downloadRequest;

	// Version 7
	void downloadAudioFile(DownloadManager.Request request)
	{
		downloadRequest = request;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
		{
			final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
			if(manager != null)
				manager.enqueue(downloadRequest);
		}
		else
		{
			// Should we show an explanation? TODO: You can do it in the same way JarwanIOT.Raaqeb
			//if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
			//{
				// Show an explanation to the user *asynchronously* -- don't block
				// this thread waiting for the user's response! After the user
				// sees the explanation, try again to request the permission.

				//System.out.println("shouldShowRequestPermissionRationale");
			//}
			//else
			{
				// No explanation needed, we can request the permission.
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, DOWNLOAD_PERMISSION);
				// DOWNLOAD_PERMISSION is an app-defined int constant. The callback method gets the result of the request.
			}
		}
	}

	// Version 7
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		switch (requestCode)
		{
			case DOWNLOAD_PERMISSION:
			{
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
				{
					// permission is granted. Do the job
					final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
					if (manager != null)
						manager.enqueue(downloadRequest);
				}
				else
					Log.v(TAG, "request denied");
				break;
			}

			case DIRECTORY_CHOOSER_PERMISSION:
			{
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
				{
					if (sdPath == null)
						Toast.makeText(this, "App does not have any place to save files! Restart App", Toast.LENGTH_LONG).show();
					else
					{
						// permission is granted. Do the job
						final Intent intent = new Intent(this, AppRecordingPath.class);
						intent.putExtra("sdPath", sdPath);
						startActivityForResult(intent, ACTIVITY_RECORDING_LOCATION);
					}
				}
				else
					Log.v(TAG, "request denied");
				break;
			}
		}
	}

	static class SectionsPagerAdapter extends FragmentStateAdapter
	{
		SectionsPagerAdapter(FragmentManager fm, @NonNull Lifecycle lc)
		{
			super(fm, lc);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position)
		{
			switch (position)
			{
				case 0:
					return new SheekhListFragment();
				case 1:
					return new FeqhListFragment();
				case 2:
					return new SearchListFragment();
				default:
					return new FavoriteListFragment();
			}
		}

		@Override
		public int getItemCount()
		{
			return 4;
		}
	}

	enum listLevel
	{
		SHEEKH, BOOK, SUB_BOOK, CHAPTER, INDEX
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK)
		{
			if (requestCode == ACTIVITY_RECORDING_LOCATION)
			{
				sdPath = data.getStringExtra("sdPath");
				mPrefs.edit().putString("sdPath", sdPath).apply();
			}
		}
	}

	@Override
	public boolean handleMessage(@NonNull Message msg)
	{
		//final String input = (String) msg.obj;
		if (msg.what == MSG_DB_INITIAT_DONE)
		{
			setUI();
			dbInitiateDialog.dismiss();

			//recreate(); // taking time so manually we will connect mediaBrowserCompat instead of onStart()
			if (!mediaBrowserCompat.isConnected())
				mediaBrowserCompat.connect();
		}
		return true;
	}
}