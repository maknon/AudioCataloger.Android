package com.maknoon.audiocataloger;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.ListFragment;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static com.maknoon.audiocataloger.MainActivity.setCurrentChapter;
import static com.maknoon.audiocataloger.MainActivity.sheekhSelected;
import static com.maknoon.audiocataloger.MainActivity.sheekhIds;
import static com.maknoon.audiocataloger.MainActivity.toURL_File;

public class SearchListFragment extends ListFragment
{
	private Context mainContext;

	@Override
	public void onAttach(@NonNull Context context)
	{
		super.onAttach(context);
		mainContext = context;
	}

	// This interface to communicate between MainActivity and this fragment
	private SearchListFragment.setOnPlayListener playCallback;
	void setOnPlayListener(SearchListFragment.setOnPlayListener playCallback)
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
		return inflater.inflate(R.layout.search_fragment, container, false);
	}

	@Override
	public void onViewCreated(@NonNull final View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);
		final AppCompatEditText editText = view.findViewById(R.id.search_src_text);
		editText.setOnEditorActionListener(new TextView.OnEditorActionListener()
		{
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				if (actionId == EditorInfo.IME_ACTION_SEARCH)
				{
					final String input = String.valueOf(v.getText()).trim();
					if (input.isEmpty())
						setListAdapter(null);
					else
					{
						if(input.length() == 1)
						{
							setListAdapter(null);
							Toast.makeText(mainContext, "جملة البحث من حرفين أو أكثر", Toast.LENGTH_LONG).show();
						}
						else
							displayResults(input);
					}

					// Hide Keyboard
					final InputMethodManager imm= (InputMethodManager)mainContext.getSystemService(INPUT_METHOD_SERVICE);
					if(imm != null)
						imm.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS); // or context.getCurrentFocus().getWindowToken()
					return true;
				}
				return false;
			}
		});

		final AppCompatImageButton button = view.findViewById(R.id.search_button);
		button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View arg0)
			{
				final String input = String.valueOf(editText.getText()).trim();
				if (input.isEmpty())
					setListAdapter(null);
				else
				{
					if(input.length() == 1)
					{
						setListAdapter(null);
						Toast.makeText(mainContext, "جملة البحث من حرفين أو أكثر", Toast.LENGTH_LONG).show();
					}
					else
						displayResults(input);
				}

				// Hide Keyboard
				final InputMethodManager imm= (InputMethodManager)mainContext.getSystemService(INPUT_METHOD_SERVICE);
				if(imm != null)
					imm.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
			}
		});
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
		final SearchNodeInfo node = (SearchNodeInfo) getListAdapter().getItem(position);
		if(node.book_name.equals(node.title))
		{
			playCallback.play(node.offset, node.duration, toURL_File(node.path, node.fileName), node.sheekh_name, node.book_name + "←" + node.fileName);
			setCurrentChapter(node.sheekh_name + "←" + node.book_name + "←" + node.fileName, node.fileName, node.path, mainContext);
		}
		else
		{
			playCallback.play(node.offset, node.duration, toURL_File(node.path, node.fileName), node.sheekh_name, node.book_name + "←" + node.title + "←" + node.fileName);
			setCurrentChapter(node.sheekh_name + "←" + node.book_name + "←" + node.title + "←" + node.fileName, node.fileName, node.path, mainContext);
		}
	}

	private void displayResults(String input)
	{
		String SQL_Combination = null;
		boolean wholeDB = true;

		for (int i=0; i < sheekhIds.length; i++)
		{
			if(sheekhSelected[i])
			{
				if (SQL_Combination != null)
					SQL_Combination = SQL_Combination + " OR Sheekh_id:" + sheekhIds[i];
				else
					SQL_Combination = "Sheekh_id:" + sheekhIds[i];
			}
			else
				wholeDB = false;
		}

		// Version 5 to allow '*' for all words search
		final String search = input.trim().replaceAll(" ", "* ")+"*"; // Suffix is not working with FTS *input

		//final Cursor mCursor = DbHelper.getWordMatches(input, null);
		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor = db.rawQuery("SELECT Code, Seq, Offset, Line, Duration, Tafreeg FROM Contents_FTS WHERE Contents_FTS MATCH ? LIMIT 50", new String[]{"Line:"+search+(wholeDB?"":" "+SQL_Combination)});

		if(mCursor == null || !mCursor.moveToFirst())
			setListAdapter(null);
		else
		{
			final SearchNodeInfo[] values = new SearchNodeInfo[mCursor.getCount()];
			for (int i = 0; i < mCursor.getCount(); i++)
			{
				final int code = mCursor.getInt(mCursor.getColumnIndexOrThrow("Code"));
				final int seq = mCursor.getInt(mCursor.getColumnIndexOrThrow("Seq"));
				final int offset = Integer.parseInt(mCursor.getString(mCursor.getColumnIndexOrThrow("Offset")));
				final int duration = Integer.parseInt(mCursor.getString(mCursor.getColumnIndexOrThrow("Duration")));
				final String line = mCursor.getString(mCursor.getColumnIndexOrThrow("Line"));
				final String tafreeg = mCursor.getString(mCursor.getColumnIndexOrThrow("Tafreeg"));

				final Cursor mCursor1 = db.rawQuery("SELECT Sheekh_name, Book_name, Title, FileName, Path FROM Chapters WHERE Code = " + code, null);
				mCursor1.moveToFirst();
				final String sheekh_name = mCursor1.getString(mCursor1.getColumnIndexOrThrow("Sheekh_name"));
				final String book_name = mCursor1.getString(mCursor1.getColumnIndexOrThrow("Book_name"));
				final String title = mCursor1.getString(mCursor1.getColumnIndexOrThrow("Title"));
				final String fileName = mCursor1.getString(mCursor1.getColumnIndexOrThrow("FileName"));
				final String path = mCursor1.getString(mCursor1.getColumnIndexOrThrow("Path"));
				mCursor1.close();

				values[i] = new SearchNodeInfo(line, offset, duration, sheekh_name, book_name, title, fileName, path, input.trim(), tafreeg, seq);
				mCursor.moveToNext();
			}
			mCursor.close();
			db.close();
			mDbHelper.close();

			final SearchAdapter adapter = new SearchAdapter(mainContext, values);
			setListAdapter(adapter);
		}
	}
}
