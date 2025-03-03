package com.maknoon.audiocataloger;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.ListFragment;

import static com.maknoon.audiocataloger.MainActivity.setCurrentChapter;
import static com.maknoon.audiocataloger.MainActivity.sheekhIds;
import static com.maknoon.audiocataloger.MainActivity.sheekhSelected;
import static com.maknoon.audiocataloger.MainActivity.toURL_File;

public class FeqhListFragment extends ListFragment
{
	private Context mainContext;

	@Override
	public void onAttach(@NonNull Context context)
	{
		super.onAttach(context);
		mainContext = context;
	}

	// This interface to communicate between MainActivity and this fragment
	private FeqhListFragment.setOnPlayListener playCallback;
	void setOnPlayListener(FeqhListFragment.setOnPlayListener playCallback)
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
		return inflater.inflate(R.layout.feqh_fragment, container, false);
	}

	private AppCompatTextView tv;
	private AppCompatImageButton button;

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		tv = view.findViewById(R.id.listHeader);

		button = view.findViewById(R.id.button);
		button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View arg0)
			{
				final FeqhNodeInfo node = (FeqhNodeInfo) getListAdapter().getItem(0);
				displayFeqhParent(node.category_parent);
			}
		});

		displayFeqhChild(0, ""); // Empty means root
	}

	// Version 7
	boolean back()
	{
		if (button.isShown())
		{
			System.out.println(button.performClick()); // Should be true
			return true;
		}
		else
			return false;
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
		final FeqhNodeInfo node = (FeqhNodeInfo) getListAdapter().getItem(position);
		if (node.isIndex)
		{
			/*
			final Cursor mCursor = DbHelper.query("SELECT Book_name, Sheekh_name, Title, FileName, Duration, Path FROM Chapters WHERE Code = " + node.code);
			final String book_name = decrypt(mCursor.getString(mCursor.getColumnIndex("Book_name")));
			final String sheekh_name = decrypt(mCursor.getString(mCursor.getColumnIndex("Sheekh_name")));
			final String title = decrypt(mCursor.getString(mCursor.getColumnIndex("Title")));
			final String fileName = mCursor.getString(mCursor.getColumnIndex("FileName"));
			final int duration = mCursor.getInt(mCursor.getColumnIndex("Duration"));
			final String path = decrypt(mCursor.getString(mCursor.getColumnIndex("Path")));
			mCursor.close();
			*/

			if (node.book_name.equals(node.title))
			{
				playCallback.play(node.offset, node.duration, toURL_File(node.path, node.fileName, node.code), node.sheekh_name, node.book_name + "←" + node.fileName);
				setCurrentChapter(node.sheekh_name + "←" + node.book_name + "←" + node.fileName, node.fileName, node.path, mainContext, node.code);
			}
			else
			{
				playCallback.play(node.offset, node.duration, toURL_File(node.path, node.fileName, node.code), node.sheekh_name, node.book_name + "←" + node.title + "←" + node.fileName);
				setCurrentChapter(node.sheekh_name + "←" + node.book_name + "←" + node.title + "←" + node.fileName, node.fileName, node.path, mainContext, node.code);
			}
		}
		else
			displayFeqhChild(node.category_id, node.category_name);
	}

	void displayFeqhChild(int id, final String name)
	{
		String SQL_Combination = null;
		boolean wholeDB = true;

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

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor1 = db.rawQuery("SELECT * FROM Category WHERE Category_parent = " + id, null);

		// Version 1.1, replace Contents with Contents_FTS
		final Cursor mCursor2 = db.rawQuery("SELECT Code, Seq, Offset, Line, Duration, Tafreeg FROM Contents_FTS WHERE Contents_FTS MATCH ? LIMIT 50", new String[]{"Category_id:" + id + (wholeDB ? "" : " " + SQL_Combination)});

		/*
		if(wholeDB)
			mCursor2 = DbHelper.query("SELECT Contents.Code AS Code, Contents.Seq AS Seq, Offset, Line, Contents.Duration As Duration, Tafreeg, Sheekh_name, Book_name, Title, FileName, Path FROM ContentCat JOIN Contents ON ContentCat.Code = Contents.Code AND ContentCat.Seq = Contents.Seq JOIN Chapters ON ContentCat.Code = Chapters.Code WHERE ContentCat.Category_id = "+id+" LIMIT 50"); // Very slow.
		else
			mCursor2 = DbHelper.query("SELECT Contents.Code AS Code, Contents.Seq AS Seq, Offset, Line, Contents.Duration As Duration, Tafreeg, Sheekh_name, Book_name, Title, FileName, Path FROM ContentCat JOIN Contents ON ContentCat.Code = Contents.Code AND ContentCat.Seq = Contents.Seq JOIN Chapters ON ContentCat.Code = Chapters.Code WHERE ContentCat.Category_id = "+id+" AND ("+SQL_Combination+") LIMIT 50");
		*/

		// Version 1.1, replace Contents with All_FTS. Removed again as well
		/*
		if(wholeDB)
			mCursor2 = DbHelper.query("SELECT Code, Seq, Offset, Line, Duration, Tafreeg, Sheekh_name, Book_name, Title, FileName, Path FROM All_FTS WHERE Category_id MATCH ? LIMIT 50", new String[]{","+id+","});
		else
			mCursor2 = DbHelper.query("SELECT Code, Seq, Offset, Line, Duration, Tafreeg, Sheekh_name, Book_name, Title, FileName, Path FROM All_FTS WHERE Category_id MATCH ? AND ("+SQL_Combination+") LIMIT 50", new String[]{","+id+","});
		*/

		final FeqhNodeInfo[] values = new FeqhNodeInfo[mCursor1.getCount() + mCursor2.getCount()];
		if (values.length == 0)
		{
			Toast.makeText(mainContext, "لا توجد أقسام أو فهارس لهذا الفرع", Toast.LENGTH_LONG).show();
			mCursor1.close();
			mCursor2.close();
			return;
		}

		int i = 0;
		if (mCursor1.moveToFirst())
		{
			for (; i < mCursor1.getCount(); i++)
			{
				final String category_name = mCursor1.getString(mCursor1.getColumnIndexOrThrow("Category_name"));
				final int category_id = mCursor1.getInt(mCursor1.getColumnIndexOrThrow("Category_id"));
				values[i] = new FeqhNodeInfo(mainContext, false, category_id, category_name, id, -1, -1, -1, -1, null, null, null, null, null, null, null);
				mCursor1.moveToNext();
			}
		}
		mCursor1.close();

		if (mCursor2.moveToFirst())
		{
			for (int j = 0; j < mCursor2.getCount(); i++, j++)
			{
				final int code = mCursor2.getInt(mCursor2.getColumnIndexOrThrow("Code"));
				final int seq = mCursor2.getInt(mCursor2.getColumnIndexOrThrow("Seq"));
				final int offset = Integer.parseInt(mCursor2.getString(mCursor2.getColumnIndexOrThrow("Offset")));
				final int duration = Integer.parseInt(mCursor2.getString(mCursor2.getColumnIndexOrThrow("Duration")));
				final String line = mCursor2.getString(mCursor2.getColumnIndexOrThrow("Line"));
				final String tafreeg = mCursor2.getString(mCursor2.getColumnIndexOrThrow("Tafreeg"));

				final Cursor mCursor3 = db.rawQuery("SELECT Sheekh_name, Book_name, Title, FileName, Path FROM Chapters WHERE Code = " + code, null); // PreparedStatement is not available for Android.check compilestatement.
				mCursor3.moveToFirst();
				final String book_name = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Book_name"));
				final String sheekh_name = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Sheekh_name"));
				final String title = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Title"));
				final String fileName = mCursor3.getString(mCursor3.getColumnIndexOrThrow("FileName"));
				final String path = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Path"));
				mCursor3.close();

				values[i] = new FeqhNodeInfo(mainContext, true, -1, null, id, code, offset, duration, seq, line, tafreeg, sheekh_name, book_name, title, fileName, path);
				mCursor2.moveToNext();
			}
		}
		mCursor2.close();
		db.close();
		mDbHelper.close();

		//final ArrayAdapter<FeqhNodeInfo> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, values);
		final FeqhAdapter adapter = new FeqhAdapter(mainContext, values);
		setListAdapter(adapter);

		if (id == 0)
		{
			button.setVisibility(View.INVISIBLE);
			tv.setText("");
		}
		else
		{
			button.setVisibility(View.VISIBLE);
			final String str = (String) tv.getText();
			if (str.equals(""))
				tv.setText(name);
			else
				tv.setText(tv.getText() + "←" + name);
		}
	}

	private void displayFeqhParent(int id)
	{
		String SQL_Combination = null;
		boolean wholeDB = true;

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

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor0 = db.rawQuery("SELECT Category_parent from Category WHERE Category_id = " + id, null);
		mCursor0.moveToFirst();
		final int parent = mCursor0.getInt(mCursor0.getColumnIndexOrThrow("Category_parent")); // There should be a parent (2 levels up), otherwise this function should not be called.
		mCursor0.close();

		//final Cursor mCursor1 = DbHelper.query("SELECT * FROM Category WHERE Category_parent = (SELECT Category_parent from Category WHERE Category_id = "+id+")");
		final Cursor mCursor1 = db.rawQuery("SELECT * FROM Category WHERE Category_parent = " + parent, null);

		// Version 1.1, replace Contents with Contents_FTS
		final Cursor mCursor2 = db.rawQuery("SELECT Code, Seq, Offset, Line, Duration, Tafreeg FROM Contents_FTS WHERE Contents_FTS MATCH ? LIMIT 50", new String[]{"Category_id:" + parent + (wholeDB ? "" : " " + SQL_Combination)});

		/*
		if(wholeDB)
			mCursor2 = DbHelper.query("SELECT Contents.Code AS Code, Contents.Seq AS Seq, Offset, Line, Contents.Duration As Duration, Tafreeg, Sheekh_name, Book_name, Title, FileName, Path FROM ContentCat JOIN Contents ON ContentCat.Code = Contents.Code AND ContentCat.Seq = Contents.Seq JOIN Chapters ON ContentCat.Code = Chapters.Code WHERE ContentCat.Category_id = "+parent+" LIMIT 50"); // Very slow.
		else
			mCursor2 = DbHelper.query("SELECT Contents.Code AS Code, Contents.Seq AS Seq, Offset, Line, Contents.Duration As Duration, Tafreeg, Sheekh_name, Book_name, Title, FileName, Path FROM ContentCat JOIN Contents ON ContentCat.Code = Contents.Code AND ContentCat.Seq = Contents.Seq JOIN Chapters ON ContentCat.Code = Chapters.Code WHERE ContentCat.Category_id = "+parent+" AND ("+SQL_Combination+") LIMIT 50");
		*/

		final FeqhNodeInfo[] values = new FeqhNodeInfo[mCursor1.getCount() + mCursor2.getCount()];

		int i = 0;
		if (mCursor1.moveToFirst())
		{
			for (; i < mCursor1.getCount(); i++)
			{
				final String category_name = mCursor1.getString(mCursor1.getColumnIndexOrThrow("Category_name"));
				final int category_id = mCursor1.getInt(mCursor1.getColumnIndexOrThrow("Category_id"));
				//final int category_parent = parent = mCursor1.getInt(mCursor1.getColumnIndex("Category_parent"));
				values[i] = new FeqhNodeInfo(mainContext, false, category_id, category_name, parent, -1, -1, -1, -1, null, null, null, null, null, null, null);
				mCursor1.moveToNext();
			}
		}
		mCursor1.close();

		if (mCursor2.moveToFirst())
		{
			for (int j = 0; j < mCursor2.getCount(); i++, j++)
			{
				final int code = mCursor2.getInt(mCursor2.getColumnIndexOrThrow("Code"));
				final int seq = mCursor2.getInt(mCursor2.getColumnIndexOrThrow("Seq"));
				final int offset = Integer.parseInt(mCursor2.getString(mCursor2.getColumnIndexOrThrow("Offset")));
				final int duration = Integer.parseInt(mCursor2.getString(mCursor2.getColumnIndexOrThrow("Duration")));
				final String line = mCursor2.getString(mCursor2.getColumnIndexOrThrow("Line"));
				final String tafreeg = mCursor2.getString(mCursor2.getColumnIndexOrThrow("Tafreeg"));

				final Cursor mCursor3 = db.rawQuery("SELECT Sheekh_name, Book_name, Title, FileName, Path FROM Chapters WHERE Code = " + code, null);
				mCursor3.moveToFirst();
				final String book_name = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Book_name"));
				final String sheekh_name = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Sheekh_name"));
				final String title = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Title"));
				final String fileName = mCursor3.getString(mCursor3.getColumnIndexOrThrow("FileName"));
				final String path = mCursor3.getString(mCursor3.getColumnIndexOrThrow("Path"));
				mCursor3.close();

				values[i] = new FeqhNodeInfo(mainContext, true, -1, null, id, code, offset, duration, seq, line, tafreeg, sheekh_name, book_name, title, fileName, path);
				mCursor2.moveToNext();
			}
		}
		mCursor2.close();
		db.close();
		mDbHelper.close();

		final FeqhAdapter adapter = new FeqhAdapter(mainContext, values);
		setListAdapter(adapter);

		if (parent == 0)
		{
			button.setVisibility(View.INVISIBLE);
			tv.setText("");
		}
		else
		{
			button.setVisibility(View.VISIBLE);

			final String str = (String) tv.getText();
			tv.setText(str.substring(0, str.lastIndexOf("←")));
		}
	}
}
