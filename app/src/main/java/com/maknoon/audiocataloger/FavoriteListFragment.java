package com.maknoon.audiocataloger;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.ListFragment;

import java.util.ArrayList;
import java.util.Arrays;

import static com.maknoon.audiocataloger.MainActivity.setCurrentChapter;
import static com.maknoon.audiocataloger.MainActivity.toURL_File;

public class FavoriteListFragment extends ListFragment
{
	private Context mainContext;

	@Override
	public void onAttach(@NonNull Context context)
	{
		super.onAttach(context);
		mainContext = context;
	}

	// This interface to communicate between MainActivity and this fragment
	private FavoriteListFragment.setOnPlayListener playCallback;
	void setOnPlayListener(FavoriteListFragment.setOnPlayListener playCallback)
	{
		this.playCallback = playCallback;
	}

	// This interface will be implemented by the MainActivity
	public interface setOnPlayListener
	{
		void play(final int offset, final int duration, final String url, final String title, final String subTitle);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		super.onCreateView(inflater, container, savedInstanceState);
		return inflater.inflate(R.layout.favorite_fragment, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		displayFavoriteList();
	}

	@Override
	public void onDestroyView()
	{
		setListAdapter(null);
		super.onDestroyView();
	}

	@Override
	public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id)
	{
		final FavoriteNodeInfo node = (FavoriteNodeInfo) getListAdapter().getItem(position);
		playCallback.play(node.offset, -1, toURL_File(node.path, node.fileName), node.reference, node.fileName);
		setCurrentChapter(node.reference, node.fileName, node.path, mainContext);
	}

	void displayFavoriteList()
	{
		/* Not working, http://stackoverflow.com/questions/32157862/set-add-in-android-sharedpreferences-is-not-working-as-expected
		final SharedPreferences favoriteList = context.getSharedPreferences("favoriteList", MODE_PRIVATE);

		final Set<String> reference = favoriteList.getStringSet("reference", new HashSet<String>());
		final Set<String> path = favoriteList.getStringSet("path", new HashSet<String>());
		final Set<String> fileName = favoriteList.getStringSet("fileName", new HashSet<String>());
		final Set<String> offset = favoriteList.getStringSet("offset", new HashSet<String>());

		final Iterator<String> ref = reference.iterator();
		final Iterator<String> pa = path.iterator();
		final Iterator<String> fi = fileName.iterator();
		final Iterator<String> of = offset.iterator();

		Log.v("maknoon:Main", "displayFavoriteList: "+reference.size());
		Log.v("maknoon:Main", "displayFavoriteList: "+path.size());
		Log.v("maknoon:Main", "displayFavoriteList: "+fileName.size());
		Log.v("maknoon:Main", "displayFavoriteList: "+offset.size());
		*/

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor = db.rawQuery("SELECT * FROM Favorite", null);
		final FavoriteNodeInfo[] values = new FavoriteNodeInfo[mCursor.getCount()];
		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < values.length; i++)
			{
				String reference = mCursor.getString(mCursor.getColumnIndexOrThrow("Reference"));
				final String path = mCursor.getString(mCursor.getColumnIndexOrThrow("path"));
				final String fileName = mCursor.getString(mCursor.getColumnIndexOrThrow("FileName"));
				final int Offset = mCursor.getInt(mCursor.getColumnIndexOrThrow("Offset"));
				final int rowid = mCursor.getInt(mCursor.getColumnIndexOrThrow("rowid"));

				final String hour = String.valueOf(Offset / 3600 / 1000);
				final String minute = String.valueOf(Offset / 60 / 1000 - (Offset / 3600 / 1000) * 60);
				final String second = String.valueOf(Offset / 1000 - ((int) ((float) Offset / 60F / 1000F) * 60));
				reference = "[" + hour + ":" + minute + ":" + second + "] " + reference;

				values[i] = new FavoriteNodeInfo(reference, Offset, fileName, path, rowid);
				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

		final ArrayList<FavoriteNodeInfo> list = new ArrayList<>(Arrays.asList(values));
		setListAdapter(new FavoriteAdapter(mainContext, list));
	}
}

