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
import androidx.annotation.OptIn;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.PlayerControlView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import androidx.appcompat.app.AppCompatDelegate;

import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayoutMediator;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@OptIn(markerClass = UnstableApi.class)
public class MainActivity extends AppCompatActivity implements Handler.Callback,
		SheekhListFragment.setOnPlayListener, SearchListFragment.setOnPlayListener,
		FeqhListFragment.setOnPlayListener, FavoriteListFragment.setOnPlayListener
{
	final private static String TAG = "MainActivity";

	static CharSequence[] sheekhNames;
	static int[] sheekhIds;
	static boolean[] sheekhSelected;

	static String sdPath, sheekh_ids;
	SharedPreferences mPrefs;

	static final int MSG_DB_INITIAT_DONE = 1;

	ProgressDialog dbInitiateDialog;

	Handler handler;

	PlayerControlView exoPlayerView;
	View bookmarkButton;

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

	String runningUrl = "";

	@Override
	public void play(final int offset, final int duration, final String url, final String title, final String subTitle)
	{
		bookmarkButton.setEnabled(true);
		bookmarkButton.setAlpha(1f);

		if (mediaController != null)
		{
			 // TODO duration should be used to stop the playing after period = duration
			final MediaMetadata metadata = new MediaMetadata.Builder()
					.setAlbumTitle(title) // ("\u200e" + title) we put LTR mark since subTitle cannot be changed to RTL using Mark '\u200f', not working
					.setTitle(subTitle)
					.build();
			final MediaItem media = new MediaItem.Builder().setMediaId(url).setMediaMetadata(metadata).build();

			if (runningUrl.equals(url) && mediaController.getPlaybackState() == Player.STATE_READY)
				mediaController.seekTo(0, offset);
			else
			{
				if (offset != 0)
				{
					mediaController.setMediaItem(media, false);
					mediaController.seekTo(0, offset);
				}
				else
					// Prepare the player with the source
					mediaController.setMediaItem(media, true);

				//mediaController.addMediaItem(media); // This is to enable the bookmark for the playerView only (but disabled for the notification by overriding isCommandAvailable() in MediaService). This media item is dummy. we will override the action for next button

				mediaController.prepare();
				runningUrl = url;
			}
			mediaController.setPlayWhenReady(true);
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
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
			registerReceiver(DownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
		else
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
				setUI();
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

			copyDataBase(DBHelper.DB_NAME); // you cannot delete db in the asset folder after installation. apk is read only
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

			//db.execSQL("CREATE VIRTUAL TABLE Contents_FTS USING fts4(matchinfo=fts3, Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Book_id INTEGER, Offset TEXT, Duration TEXT, Category_id TEXT, Line TEXT, Tafreeg TEXT)");
			db.execSQL("CREATE VIRTUAL TABLE Contents_FTS USING fts4 (Code, Seq, Sheekh_id, Book_id, \"Offset\", Duration, Category_id, Line, Tafreeg, notindexed=Seq, notindexed=Book_id, notindexed=\"Offset\", notindexed=Duration, notindexed=Tafreeg)"); // Android 4.2 is not supporting FTS4 notindexed only 5.0 is supporting this

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

		exoPlayerView = findViewById(R.id.playerView);

		/*
		PlayerControlView cannot be customised to add the bookmark button. We can replace the action for next button
		to act as the saveFavorite/bookmark button, but it enable and disable the button based on the playlist, hence it is not suitable.
		Replaced with normal button in the layout. We are adding a dummy MediaItem to enable the next button and override its action
		*/
		exoPlayerView.findViewById(androidx.media3.ui.R.id.exo_next).setVisibility(View.GONE);
		bookmarkButton = exoPlayerView.findViewById(R.id.exo_bookmark);
		bookmarkButton.setEnabled(false);
		bookmarkButton.setAlpha(33f / 100); // https://github.com/androidx/media/blob/main/libraries/ui/src/main/res/values/constants.xml
		bookmarkButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View view)
			{
				saveFavorite();
			}
		});

		((MaterialToolbar)findViewById(R.id.toolbar)).setOnMenuItemClickListener(this::onMenuItemSelected);

		final DBHelper mDbHelper = new DBHelper(this);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor = db.rawQuery("SELECT * FROM Sheekh ORDER BY Sheekh_id", null);

		sheekhNames = new CharSequence[mCursor.getCount()];
		sheekhIds = new int[mCursor.getCount()];
		sheekhSelected = new boolean[mCursor.getCount()];

		sheekh_ids = mPrefs.getString("sheekh_ids", "");

		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < mCursor.getCount(); i++)
			{
				final String sheekh_name = mCursor.getString(mCursor.getColumnIndexOrThrow("Sheekh_name"));
				final int sheekh_id = mCursor.getInt(mCursor.getColumnIndexOrThrow("Sheekh_id"));
				sheekhNames[i] = sheekh_name;
				sheekhIds[i] = sheekh_id;

				if (sheekh_ids.contains(" " + sheekh_id + " "))
					sheekhSelected[i] = false;
				else
					sheekhSelected[i] = true;
				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

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
				else if (position == 1)
					tab.setText(R.string.title_section2);
				else if (position == 2)
					tab.setIcon(R.drawable.sharp_search_24);
				else if (position == 3)
					tab.setIcon(R.drawable.outline_bookmark_border_24);
			}
		}).attach();

		mViewPager.setCurrentItem(0, true);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
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

	//AlertDialog directoryDialog;
	//ListView directoryList;
	public boolean onMenuItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.search_range:
			{
				final MaterialAlertDialogBuilder ad = new MaterialAlertDialogBuilder(this);
				ad.setTitle(R.string.search_range);
				ad.setMultiChoiceItems(sheekhNames, sheekhSelected, new DialogInterface.OnMultiChoiceClickListener()
				{
					public void onClick(DialogInterface dialog, int clicked, boolean selected)
					{
						//Log.i("maknoon", sheekhNames[clicked] + " selected: " + selected);
					}
				});

				ad.setPositiveButton(R.string.remove_all, new DialogInterface.OnClickListener()
				{
					// This will not override the behavior of this button (close after click)
					public void onClick(DialogInterface dialog, int id)
					{
					}
				});

				ad.setNegativeButton(R.string.select_all, new DialogInterface.OnClickListener()
				{
					// This will not override the behavior of this button (close after click)
					public void onClick(DialogInterface dialog, int id)
					{
					}
				});

				ad.setNeutralButton(R.string.close, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int id)
					{
						//for (int i = 0; i < sheekhNames.length; i++)
						//	Log.i("maknoon", sheekhNames[i] + " selected: " + sheekhSelected[i]);

						sheekh_ids = "";
						for (int i = 0; i < sheekhIds.length; i++)
							if (!sheekhSelected[i])
								sheekh_ids = sheekh_ids + " " + sheekhIds[i] + " ";

						mPrefs.edit().putString("sheekh_ids", sheekh_ids).apply();

						dialog.cancel();

						// Refresh the whole view
						((SheekhListFragment) getSupportFragmentManager().getFragments().get(0)).displaySheekh();
						((FeqhListFragment) getSupportFragmentManager().getFragments().get(1)).displayFeqhChild(0, ""); // Empty means root
					}
				});

				final AlertDialog d = ad.create();
				d.setCancelable(false);
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
				final MaterialAlertDialogBuilder ad = new MaterialAlertDialogBuilder(context);
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

	MediaController mediaController;

	@Override
	protected void onStart()
	{
		super.onStart();
		Log.v(TAG, "onStart");

		final SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MediaService.class));

		if(mediaController == null)
		{
			final MediaController.Builder builder = new MediaController.Builder(this, sessionToken)
					.setListener(new MediaController.Listener()
					{
						@Override
						public void onDisconnected(@NonNull MediaController controller)
						{
							MediaController.Listener.super.onDisconnected(controller);
							Log.v(TAG, "onDisconnected");
						}
					});

			final ListenableFuture<MediaController> future = builder.buildAsync();
			future.addListener(() ->
			{
				try
				{
					mediaController = future.get();
					mediaController.addListener(new Player.Listener()
					{
						@Override
						public void onPlaybackStateChanged(int playbackState)
						{
							Player.Listener.super.onPlaybackStateChanged(playbackState);
							switch (playbackState)
							{
								case Player.STATE_READY:
									if (exoPlayerView != null)
										exoPlayerView.show();
									break;
								case Player.STATE_BUFFERING:
									break;
								case Player.STATE_ENDED:
									break;
								case Player.STATE_IDLE:
									break;
							}
						}
					});

					if (exoPlayerView != null) // Needed for the first time when setUI is not called as part of onCreate()
						exoPlayerView.setPlayer(mediaController);

					final int pbState = mediaController.getPlaybackState();
					if (mediaController.isConnected() && (mediaController.getPlayWhenReady() || pbState == Player.STATE_READY))
					{
						bookmarkButton.setEnabled(true);
						bookmarkButton.setAlpha(1f);
					}

					Log.v(TAG, "The session accepted the connection");
				}
				catch (ExecutionException | InterruptedException e)
				{
					if (e.getCause() instanceof SecurityException)
					{
						Log.e(TAG, "The session rejected the connection", e);
					}
				}
			}, ContextCompat.getMainExecutor(this));
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

	private final BroadcastReceiver saveFavoriteReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context c, Intent i)
		{
			Log.v(TAG, "saveFavoriteReceiver");
			saveFavorite();
		}
	};

	private final BroadcastReceiver DownloadReceiver = new BroadcastReceiver()
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
		super.onStop();

		if (mediaController != null)
		{
			if(mediaController.isConnected())
				mediaController.release();
			mediaController = null;
		}

		//MediaController.releaseFuture(future); // no need

		if(exoPlayerView != null)
			exoPlayerView.setPlayer(null);
	}

	void saveFavorite()
	{
		Log.v(TAG, "saveFavorite");

		if (mediaController != null)
		{
			final int pbState = mediaController.getPlaybackState();
			if (mediaController.isConnected() && (mediaController.getPlayWhenReady() || pbState == Player.STATE_READY))
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
				values.put("Code", currentChapter.getInt("code", 0));
				values.put("Offset", mediaController.getCurrentPosition());

				final DBHelper mDbHelper = new DBHelper(this);
				final SQLiteDatabase db = mDbHelper.getWritableDatabase();
				db.insert("Favorite", null, values);
				db.close();

				((FavoriteListFragment) getSupportFragmentManager().getFragments().get(3)).displayFavoriteList();
			}
		}
	}

	static void setCurrentChapter(String reference, String fileName, String path, Context context, int code)
	{
		final SharedPreferences currentChapter = context.getSharedPreferences("currentChapter", MODE_PRIVATE);
		final SharedPreferences.Editor editor = currentChapter.edit();
		editor.putString("reference", reference);
		editor.putString("path", path);
		editor.putString("fileName", fileName);
		editor.putInt("code", code);
		editor.apply();
	}

	public static String toURL(String path, String fileName, int code, boolean shortUrl)
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
		builder.scheme("https")
				.authority("www.maknoon.com")
				.appendPath("audios.m4a");

		for (String token : path.split("/"))
			builder.appendPath(token);

		builder.appendPath(fileName + ".m4a");

		// Version 7
		if (shortUrl)
			return "https://fiqh.cc/?" + code;
		else
			return builder.build().toString();
	}

	public static String toURL(String path, String fileName, int seq, int code, boolean shortUrl)
	{
		final Uri.Builder builder = new Uri.Builder();
		builder.scheme("https")
				.authority("www.maknoon.com")
				.appendPath("audios.m4a.parts");

		for (String token : path.split("/"))
			builder.appendPath(token);

		builder.appendPath(fileName);
		builder.appendPath(seq + ".m4a");

		if (shortUrl) // Version 7
			return "https://fiqh.cc/?" + code + "," + seq;
		else
			return builder.build().toString();
	}

	public static String toURL_File(final String path, final String fileName, int code)
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

		return toURL(path, fileName, code, false);
	}

	public static Uri toUri(String path)
	{
		final Uri.Builder builder = new Uri.Builder();
		builder.scheme("https")
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

			if (exoPlayerView != null && mediaController != null)
				exoPlayerView.setPlayer(mediaController);

			if(!isDestroyed() && dbInitiateDialog != null  && dbInitiateDialog.isShowing())
				dbInitiateDialog.dismiss();
		}
		return true;
	}
}