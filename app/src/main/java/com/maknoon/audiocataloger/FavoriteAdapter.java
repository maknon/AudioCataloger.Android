package com.maknoon.audiocataloger;

import java.util.ArrayList;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatTextView;

public class FavoriteAdapter extends ArrayAdapter<FavoriteNodeInfo>
{
	private final Context context;
	private final ArrayList<FavoriteNodeInfo> favoriteArrayList;

	FavoriteAdapter(Context context, ArrayList<FavoriteNodeInfo> favorite_listview)
	{
		super(context, R.layout.favorite_listview, favorite_listview);

		this.context = context;
		this.favoriteArrayList = favorite_listview;
	}

	@Override
	@NonNull
	public View getView(final int position, View convertView, @NonNull ViewGroup parent)
	{
		final View rowView;

		if (convertView == null)
		{
			final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.favorite_listview, parent, false);
		} else
			rowView = convertView;

		final AppCompatTextView titleView = rowView.findViewById(R.id.item_title);
		titleView.setText(favoriteArrayList.get(position).getTitle());

		final AppCompatImageButton deleteButton = rowView.findViewById(R.id.delete);
		deleteButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View arg0)
			{
				final DBHelper mDbHelper = new DBHelper(context);
				final SQLiteDatabase db = mDbHelper.getWritableDatabase();
				db.delete("Favorite", "rowid = " + favoriteArrayList.get(position).rowid, null);
				db.close();
				favoriteArrayList.remove(position);
				notifyDataSetChanged();
			}
		});

		return rowView;
	}
}