package com.maknoon.audiocataloger;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.material.button.MaterialButton;

// https://rogerkeays.com/simple-android-file-chooser
public class AppRecordingPath extends AppCompatActivity
{
	final String TAG = "AppRecordingPath";

	private static final String PARENT_DIR = "..";

	ArrayAdapter<String> directoryList;
	ArrayAdapter<File> storageList;
	File currentPath;
	AppCompatTextView selectedFolder;
	String sdPath;

	@Override
	protected void attachBaseContext(Context base)
	{
		super.attachBaseContext(ContextWrapper.wrap(base, new Locale("ar")));
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_recording_path);
		setTitle(R.string.recording_title);

		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null)
			actionBar.setDisplayHomeAsUpEnabled(true);

		if (savedInstanceState == null)
		{
			final Intent intent = getIntent();
			sdPath = intent.getStringExtra("sdPath");
		}

		directoryList = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
		storageList = new ArrayAdapter<>(this, R.layout.app_recording_path_list, R.id.Itemname);

		final MaterialButton openFolder = findViewById(R.id.openFolder);

		// Check that there is an app activity handling folder browser intent on our system. By default there is none unless the user has some file manager app installed
		final Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.parse(sdPath), "resource/folder");
		if (intent.resolveActivityInfo(getPackageManager(), 0) != null)
		{
			openFolder.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View arg0)
				{
					final Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setDataAndType(Uri.parse(currentPath.getPath()), "resource/folder");

					// Check that there is an app activity handling that intent on our system
					//if (intent.resolveActivityInfo(getPackageManager(), 0) != null)
					startActivity(intent);
					//else
					//	Toast.makeText(getApplicationContext(), "Did not find any activity capable of handling that intent on our system", Toast.LENGTH_LONG).show();
				}
			});
		}
		else
			openFolder.setVisibility(View.GONE);

		final ListView directoryView = findViewById(R.id.directoryList);
		directoryView.setAdapter(directoryList);
		directoryView.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long rowId)
			{
				final String fileChosen = directoryList.getItem(position);
				if(fileChosen != null)
				{
					if(fileChosen.equals(PARENT_DIR))
					{
						final File chosenFile = currentPath.getParentFile();
						if (chosenFile != null && chosenFile.isDirectory())
							refresh(chosenFile);
					}
					else
					{
						final File chosenFile = new File(currentPath, fileChosen);
						if (chosenFile.isDirectory())
							refresh(chosenFile);
					}
				}
			}
		});

		final ListView storageView = findViewById(R.id.storageList);
		storageView.setAdapter(storageList);
		storageView.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long rowId)
			{
				final File chosenFile = storageList.getItem(position);
				if (chosenFile != null && chosenFile.isDirectory())
					refresh(chosenFile);
			}
		});

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
		{
			for (File f : getExternalFilesDirs(null))
			{
				if (f != null)
				{
					/* It is not useful since we do not have filesystem-level access to removable storage, except in the specific directories returned by methods like getExternalFilesDirs().
					String mPath = f.getAbsolutePath();
					final int end = mPath.indexOf("/Android");
					if(end!=-1)
						mPath = mPath.substring(0, end);
					list.add("["+mPath+"]");
					*/
					storageList.add(f);
				}
			}
		}
		else
		{
			final File f = getFilesDir();
			if(f != null)
				storageList.add(f);
		}

		selectedFolder = findViewById(R.id.txtvSelectedFolder);
		refresh(new File(sdPath));
	}

	// Sort, filter and display the files for the given path.
	private void refresh(final File path)
	{
		this.currentPath = path;
		if (path.exists())
		{
			final File[] dirs = path.listFiles(new FileFilter()
			{
				@Override
				public boolean accept(File file)
				{
					return (file.isDirectory() && file.canRead());
				}
			});

			directoryList.clear();

			if (path.getParentFile() == null || path.getParentFile().listFiles() == null)
			{
				if (dirs != null)
					for (File dir : dirs)
						directoryList.add(dir.getName());
			}
			else
			{
				directoryList.add(PARENT_DIR);

				if (dirs != null)
					for (File dir : dirs)
						directoryList.add(dir.getName());
			}

			// refresh the user interface
			selectedFolder.setText(currentPath.getPath());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.app_recording_path, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;

			case R.id.save:
				try
				{
					final File f = new File(currentPath.toString() + "/test.a");

					if (f.createNewFile())
					{
						sdPath = currentPath + "/";

						final Intent data = new Intent();
						data.putExtra("sdPath", sdPath);
						setResult(RESULT_OK, data);
						finish();

						Toast.makeText(this, R.string.recordingNotification, Toast.LENGTH_LONG).show();
						Log.i(TAG, "Audio Folder is OK, removing the temp file: " + f.delete());
					}
					else
						Toast.makeText(this, R.string.recording_folder_error, Toast.LENGTH_LONG).show();
				}
				catch (IOException e)
				{
					Toast.makeText(this, R.string.recording_folder_error, Toast.LENGTH_LONG).show();
					Log.e(TAG, Log.getStackTraceString(e));
				}

				return true;
			case R.id.create_folder:

				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.create_folder);

				final AppCompatEditText input = new AppCompatEditText(this);
				input.setInputType(InputType.TYPE_CLASS_TEXT);

				builder.setView(input);
				builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						dialog.dismiss();
						final String m_Text = input.getText().toString();
						if (!m_Text.isEmpty())
						{
							final File newDir = new File(currentPath.toString() + File.separator + m_Text);
							if (newDir.exists())
								Toast.makeText(AppRecordingPath.this, R.string.create_folder_error_already_exists, Toast.LENGTH_SHORT).show();
							else
							{
								if (newDir.mkdir())
								{
									Toast.makeText(AppRecordingPath.this, R.string.create_folder_success, Toast.LENGTH_SHORT).show();
									refresh(currentPath);
								}
								else
									Toast.makeText(AppRecordingPath.this, R.string.create_folder_error, Toast.LENGTH_SHORT).show();
							}
						}
						else
							Toast.makeText(AppRecordingPath.this, R.string.create_folder_empty, Toast.LENGTH_SHORT).show();
					}
				});
				builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});

				builder.show();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}
}