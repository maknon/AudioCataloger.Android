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
import static com.maknoon.audiocataloger.MainActivity.toURL_File;

public class SheekhListFragment extends ListFragment
{
	private MainActivity.listLevel currentLevel = MainActivity.listLevel.SHEEKH;
	private Context mainContext;

	@Override
	public void onAttach(@NonNull Context context)
	{
		super.onAttach(context);
		mainContext = context;
	}

	// This interface to communicate between MainActivity and this fragment
	private setOnPlayListener playCallback;
	void setOnPlayListener(setOnPlayListener playCallback)
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
		return inflater.inflate(R.layout.sheekh_fragment, container, false);
	}

	private AppCompatTextView tv;
	private AppCompatImageButton button;

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState)
	{
		super.onViewCreated(view, savedInstanceState);

		//final View header = getActivity().getLayoutInflater().inflate(R.layout.header, null);
		//if (header != null)
		{
			//getListView().addHeaderView(header); // will scroll with the list. Not fixed header
			tv = view.findViewById(R.id.listHeader);
			//getListView().addHeaderView(tv);
		}

		/*
		tv = new AppCompatTextView(getActivity());
		tv.setTypeface(null, Typeface.BOLD);
		tv.setTextSize(20);
		tv.setTextColor(Color.BLACK);
		tv.setPadding(0, 15, 0, 15);
		getListView().addHeaderView(tv);
		*/

		button = view.findViewById(R.id.button);
		button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View arg0)
			{
				if (currentLevel == MainActivity.listLevel.INDEX)
					displayChapter(0); // to get the first index object. in case of addHeaderView(), this should be one since header is consider the first item
				else
				{
					if (currentLevel == MainActivity.listLevel.CHAPTER)
					{
						final Chapter chapter = (Chapter) getListAdapter().getItem(0);
						if (chapter.multi_volume)
							displaySubBook(0);
						else
							displayBook(0);
					}
					else
					{
						if (currentLevel == MainActivity.listLevel.SUB_BOOK)
							displayBook(0);
						else
						{
							if (currentLevel == MainActivity.listLevel.BOOK)
								displaySheekh();
						}
					}
				}
			}
		});

		displaySheekh();
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
		if (currentLevel == MainActivity.listLevel.SHEEKH)
			displayBook(position);
		else
		{
			if (currentLevel == MainActivity.listLevel.BOOK)
			{
				final Book book = (Book) getListAdapter().getItem(position);
				if (book.multi_volume)
					displaySubBook(position);
				else
					displayChapter(position);
			}
			else
			{
				if (currentLevel == MainActivity.listLevel.SUB_BOOK)
					displayChapter(position);
				else
				{
					if (currentLevel == MainActivity.listLevel.CHAPTER)
						displayIndex(position, v);
					else
					{
						final Index index = (Index) getListAdapter().getItem(position);
						//if(index.multi_volume)
						//	setCurrentChapter(index.sheekh_name + "←" + index.book + "←" + index.sub_book + "←" + index.fileName, index.fileName, index.path);
						//else
						{
							if (index.book.equals(index.title))
							{
								playCallback.play(index.offset, index.duration, toURL_File(index.path, index.fileName), index.sheekh_name, index.book + "←" + index.fileName);
								setCurrentChapter(index.sheekh_name + "←" + index.book + "←" + index.fileName, index.fileName, index.path, mainContext);
							}
							else
							{
								playCallback.play(index.offset, index.duration, toURL_File(index.path, index.fileName), index.sheekh_name, index.book + "←" + index.title + "←" + index.fileName);

								// This will work for multi_volume as well since title = sub_book in all cases when multi_volume = true
								setCurrentChapter(index.sheekh_name + "←" + index.book + "←" + index.title + "←" + index.fileName, index.fileName, index.path, mainContext);
							}
						}
					}
				}
			}
		}
	}

	void displaySheekh()
	{
		button.setVisibility(View.INVISIBLE);

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor = db.rawQuery("SELECT * FROM Sheekh ORDER BY Sheekh_id", null);
		final Sheekh[] values = new Sheekh[mCursor.getCount()];
		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < values.length; i++)
			{
				final String sheekh_name = mCursor.getString(mCursor.getColumnIndexOrThrow("Sheekh_name"));
				final int sheekh_id = mCursor.getInt(mCursor.getColumnIndexOrThrow("Sheekh_id"));
				values[i] = new Sheekh(sheekh_id, sheekh_name);
				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

		tv.setText("");

		final SheekhAdapter<Sheekh> adapter = new SheekhAdapter<>(getActivity(), values);
		setListAdapter(adapter);

		currentLevel = MainActivity.listLevel.SHEEKH;
	}

	void refresh()
	{
		((SheekhAdapter) getListAdapter()).notifyDataSetChanged();
	}

	// Version 7
	boolean back()
	{
		if (button.isShown()) //button.getVisibility() button.setVisibility(View.VISIBLE);
		{
			System.out.println(button.performClick());
			return true;
		}
		else
			return false;
	}

	private void displayBook(final int position)
	{
		button.setVisibility(View.VISIBLE);

		Sheekh sheekh = null;
		Chapter chapter = null;
		Sub_Book sub_book = null;
		if (currentLevel == MainActivity.listLevel.SHEEKH)
			sheekh = (Sheekh) getListAdapter().getItem(position); // position-1 in case the header is part of the list and separate i.e. you use addHeaderView()
		else
		{
			if (currentLevel == MainActivity.listLevel.CHAPTER)
				chapter = (Chapter) getListAdapter().getItem(position);
			else // i.e. currentLevel == listLevel.SUB_BOOK
				sub_book = (Sub_Book) getListAdapter().getItem(position);
		}

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor;
		if (currentLevel == MainActivity.listLevel.SHEEKH)
			mCursor = db.rawQuery("SELECT Book_id, Book_name, Multi_volume FROM Book WHERE Sheekh_id = " + sheekh.id, null);
		else
		{
			if (currentLevel == MainActivity.listLevel.CHAPTER)
				mCursor = db.rawQuery("SELECT Book_id, Book_name, Multi_volume FROM Book WHERE Sheekh_id = " + chapter.sheekh_id, null);
			else
				mCursor = db.rawQuery("SELECT Book_id, Book_name, Multi_volume FROM Book WHERE Sheekh_id = " + sub_book.sheekh_id, null);
		}

		final Book[] values = new Book[mCursor.getCount()];
		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < values.length; i++)
			{
				final String book_name = mCursor.getString(mCursor.getColumnIndexOrThrow("Book_name"));
				final int book_id = mCursor.getInt(mCursor.getColumnIndexOrThrow("Book_id"));
				final boolean multi_volume = mCursor.getInt(mCursor.getColumnIndexOrThrow("Multi_volume")) == 1;

				if (currentLevel == MainActivity.listLevel.SHEEKH)
					values[i] = new Book(book_id, book_name, multi_volume, sheekh.id, sheekh.name);
				else
				{
					if (currentLevel == MainActivity.listLevel.CHAPTER)
						values[i] = new Book(book_id, book_name, multi_volume, chapter.sheekh_id, chapter.sheekh_name);
					else
						values[i] = new Book(book_id, book_name, multi_volume, sub_book.sheekh_id, sub_book.sheekh_name);
				}
				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

		if (currentLevel == MainActivity.listLevel.SHEEKH)
			tv.setText(sheekh.name);
		else
		{
			if (currentLevel == MainActivity.listLevel.CHAPTER)
				tv.setText(chapter.sheekh_name);
			else
				tv.setText(sub_book.sheekh_name);
		}

		final SheekhAdapter<Book> adapter = new SheekhAdapter<>(getActivity(), values);
		setListAdapter(adapter);

		currentLevel = MainActivity.listLevel.BOOK;
	}

	private void displaySubBook(final int position)
	{
		Book book = null;
		Chapter chapter = null;
		if (currentLevel == MainActivity.listLevel.BOOK)
			book = (Book) getListAdapter().getItem(position);
		else // i.e. currentLevel == listLevel.CHAPTER
			chapter = (Chapter) getListAdapter().getItem(position);

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor;
		if (currentLevel == MainActivity.listLevel.BOOK)
			mCursor = db.rawQuery("SELECT Title FROM Chapters WHERE Book_id = " + book.id + " GROUP BY Title", null);
		else
			mCursor = db.rawQuery("SELECT Title FROM Chapters WHERE Book_id = " + chapter.book_id + " GROUP BY Title", null);

		final Sub_Book[] values = new Sub_Book[mCursor.getCount()];
		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < values.length; i++)
			{
				final String title = mCursor.getString(mCursor.getColumnIndexOrThrow("Title"));

				if (currentLevel == MainActivity.listLevel.BOOK)
					values[i] = new Sub_Book(book.id, book.name, book.multi_volume, book.sheekh_id, book.sheekh_name, title);
				else
					values[i] = new Sub_Book(chapter.book_id, chapter.book, chapter.multi_volume, chapter.sheekh_id, chapter.sheekh_name, title);

				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

		if (currentLevel == MainActivity.listLevel.BOOK)
			tv.setText(book.sheekh_name + "←" + book.name);
		else
			tv.setText(chapter.sheekh_name + "←" + chapter.book);

		final SheekhAdapter<Sub_Book> adapter = new SheekhAdapter<>(getActivity(), values);
		setListAdapter(adapter);

		currentLevel = MainActivity.listLevel.SUB_BOOK;
	}

	private void displayChapter(final int position)
	{
		Book book = null;
		Sub_Book sub_Book = null;
		Index index = null;

		if (currentLevel == MainActivity.listLevel.BOOK)
			book = (Book) getListAdapter().getItem(position);
		else
		{
			if (currentLevel == MainActivity.listLevel.SUB_BOOK)
				sub_Book = (Sub_Book) getListAdapter().getItem(position);
			else // i.e. currentLevel == listLevel.INDEX
				index = (Index) getListAdapter().getItem(position);
		}

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor;
		if (currentLevel == MainActivity.listLevel.BOOK)
			mCursor = db.rawQuery("SELECT Title, FileName, Path, Code, Duration FROM Chapters WHERE Book_id = ? ORDER BY FileName", new String[]{String.valueOf(book.id)});
		else
		{
			if (currentLevel == MainActivity.listLevel.SUB_BOOK)
				mCursor = db.rawQuery("SELECT Title, FileName, Path, Code, Duration FROM Chapters WHERE (Book_id = ? AND Title = ?) ORDER BY FileName", new String[]{String.valueOf(sub_Book.book_id), sub_Book.title});
			else
			{
				// i.e. currentLevel == listLevel.INDEX
				if (index.multi_volume)
					mCursor = db.rawQuery("SELECT Title, FileName, Path, Code, Duration FROM Chapters WHERE (Book_id = ? AND Title = ?) ORDER BY FileName", new String[]{String.valueOf(index.book_id), index.sub_book});
				else
					mCursor = db.rawQuery("SELECT Title, FileName, Path, Code, Duration FROM Chapters WHERE Book_id = ? ORDER BY FileName", new String[]{String.valueOf(index.book_id)});
			}
		}

		final Chapter[] values = new Chapter[mCursor.getCount()];
		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < values.length; i++)
			{
				final String title = mCursor.getString(mCursor.getColumnIndexOrThrow("Title"));
				final String fileName = mCursor.getString(mCursor.getColumnIndexOrThrow("FileName"));
				final String path = mCursor.getString(mCursor.getColumnIndexOrThrow("Path"));
				final int code = mCursor.getInt(mCursor.getColumnIndexOrThrow("Code"));
				final int duration = mCursor.getInt(mCursor.getColumnIndexOrThrow("Duration"));

				if (currentLevel == MainActivity.listLevel.BOOK)
					values[i] = new Chapter(code, title, fileName, path, duration, false, book.sheekh_id, book.sheekh_name, null, book.name, book.id);
				else
				{
					if (currentLevel == MainActivity.listLevel.SUB_BOOK)
						values[i] = new Chapter(code, title, fileName, path, duration, true, sub_Book.sheekh_id, sub_Book.sheekh_name, sub_Book.title, sub_Book.book, sub_Book.book_id);
					else
					{
						if (index.multi_volume)
							values[i] = new Chapter(code, title, fileName, path, duration, true, index.sheekh_id, index.sheekh_name, index.sub_book, index.book, index.book_id);
						else
							values[i] = new Chapter(code, title, fileName, path, duration, false, index.sheekh_id, index.sheekh_name, null, index.book, index.book_id);
					}
				}

				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

		if (currentLevel == MainActivity.listLevel.BOOK)
			tv.setText(book.sheekh_name + "←" + book.name);
		else
		{
			if (currentLevel == MainActivity.listLevel.SUB_BOOK)
			{
				if (sub_Book.book.equals(sub_Book.title))
					tv.setText(sub_Book.sheekh_name + "←" + sub_Book.book);
				else
					tv.setText(sub_Book.sheekh_name + "←" + sub_Book.book + "←" + sub_Book.title);
			}
			else
			{
				if (index.multi_volume)
					tv.setText(index.sheekh_name + "←" + index.book + "←" + index.sub_book);
				else
					tv.setText(index.sheekh_name + "←" + index.book);
			}
		}

		final SheekhAdapter<Chapter> adapter = new SheekhAdapter<>(getActivity(), values);
		setListAdapter(adapter);

		currentLevel = MainActivity.listLevel.CHAPTER;
	}

	private void displayIndex(final int position, final View v)
	{
		final Chapter chapter = (Chapter) getListAdapter().getItem(position);

		final DBHelper mDbHelper = new DBHelper(mainContext);
		final SQLiteDatabase db = mDbHelper.getReadableDatabase();
		final Cursor mCursor = db.rawQuery("SELECT Seq, Offset, Duration, Line, Tafreeg FROM Contents_FTS WHERE Contents_FTS MATCH 'Code:" + chapter.code + "' ORDER BY Seq", null);
		final Index[] values = new Index[mCursor.getCount()];

		if (values.length == 0)
		{
			Toast.makeText(mainContext, "لا توجد فهارس لهذا الشريط", Toast.LENGTH_LONG).show();
			mCursor.close();
			db.close();
			mDbHelper.close();

			v.findViewById(R.id.listen).performClick();

			return;
		}

		if (mCursor.moveToFirst())
		{
			for (int i = 0; i < values.length; i++)
			{
				final int seq = mCursor.getInt(mCursor.getColumnIndexOrThrow("Seq")); //Log.v("maknoon:Main", "seq: "+seq);
				final String line = mCursor.getString(mCursor.getColumnIndexOrThrow("Line"));
				final String tafreeg = mCursor.getString(mCursor.getColumnIndexOrThrow("Tafreeg"));
				final int offset = Integer.parseInt(mCursor.getString(mCursor.getColumnIndexOrThrow("Offset")));
				final int duration = Integer.parseInt(mCursor.getString(mCursor.getColumnIndexOrThrow("Duration")));

				values[i] = new Index(chapter.code, line, tafreeg, offset, seq, duration, chapter.book_id, chapter.multi_volume, chapter.sub_book, chapter.sheekh_name, chapter.book, chapter.sheekh_id, chapter.fileName, chapter.path, chapter.title);
				mCursor.moveToNext();
			}
		}
		mCursor.close();
		db.close();
		mDbHelper.close();

		//if(chapter.multi_volume)
		//	tv.setText(chapter.sheekh_name + "←" + chapter.book + "←" + chapter.sub_book + "←" + chapter.fileName);
		//else
		{
			if (chapter.book.equals(chapter.title))
				tv.setText(chapter.sheekh_name + "←" + chapter.book + "←" + chapter.fileName);
			else
				tv.setText(chapter.sheekh_name + "←" + chapter.book + "←" + chapter.title + "←" + chapter.fileName);
		}

		final SheekhAdapter<Index> adapter = new SheekhAdapter<>(getActivity(), values);
		setListAdapter(adapter);

		currentLevel = MainActivity.listLevel.INDEX;
	}

	class Chapter implements SheekhInterface
	{
		int code;
		public String title;
		public String fileName;
		public String path;
		public int duration;

		boolean multi_volume;
		String sheekh_name;
		int sheekh_id;
		String sub_book;
		String book;
		int book_id;

		Chapter(int code, String title, String fileName, String path, int duration, boolean multi_volume, int sheekh_id, String sheekh_name, String sub_book, String book, int book_id)
		{
			this.code = code;
			this.title = title;
			this.fileName = fileName;
			this.path = path;
			this.duration = duration;

			this.multi_volume = multi_volume;
			this.sheekh_name = sheekh_name;
			this.sheekh_id = sheekh_id;
			this.sub_book = sub_book;
			this.book = book;
			this.book_id = book_id;
		}

		@Override
		public String getPath()
		{
			return path + "/" + fileName + ".m4a";
		}

		@Override
		public String shareWith()
		{
			//if(multi_volume)
			//	return sheekh_name + "←" + book + "←" + sub_book + "←" + fileName;
			//else
			{
				if (book.equals(title))
					return sheekh_name + "←" + book + "←" + fileName;
				else
					return sheekh_name + "←" + book + "←" + title + "←" + fileName;
			}
		}

		@Override
		public String getTitle()
		{
			return title + "(" + fileName + ")";
		}

		@Override
		public String getTafreeg()
		{
			return null;
		}

		@Override
		public boolean isChapter()
		{
			return true;
		}

		@Override
		public boolean isIndex()
		{
			return false;
		}

		@Override
		public boolean shareWithDisabled()
		{
			return true;
		}

		@Override
		public void playChapter()
		{
			//if(multi_volume)
			//	setCurrentChapter(sheekh_name + "←" + book + "←" + sub_book + "←" + fileName, fileName, path);
			//else
			{
				if (book.equals(title))
				{
					playCallback.play(0, -1, toURL_File(path, fileName), sheekh_name, book + "←" + fileName);
					setCurrentChapter(sheekh_name + "←" + book + "←" + fileName, fileName, path, mainContext);
				}
				else
				{
					playCallback.play(0, -1, toURL_File(path, fileName), sheekh_name, book + "←" + title + "←" + fileName);
					setCurrentChapter(sheekh_name + "←" + book + "←" + title + "←" + fileName, fileName, path, mainContext);
				}
			}
		}
	}
}