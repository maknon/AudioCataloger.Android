package com.maknoon.audiocataloger;

import java.util.Arrays;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatTextView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SearchAdapter extends ArrayAdapter<SearchNodeInfo>
{
	private final Context context;
	private final SearchNodeInfo[] searchArrayList;

	public SearchAdapter(Context context, SearchNodeInfo[] searchArrayList)
	{
		super(context, R.layout.search_listview, Arrays.asList(searchArrayList));

		this.context = context;
		this.searchArrayList = searchArrayList;
	}

	@NonNull
	@Override
	public View getView(final int position, View convertView, @NonNull ViewGroup parent)
	{
		final View rowView;

		if(convertView == null)
		{
			final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.search_listview, parent, false);
		}
		else
			rowView = convertView;

		final AppCompatTextView titleView = rowView.findViewById(R.id.result);
		final AppCompatImageButton tafreeqButton = rowView.findViewById(R.id.tafreeg);
		final AppCompatImageButton shareButton = rowView.findViewById(R.id.share);

		if(searchArrayList[position].duration == 0)
			shareButton.setVisibility(View.INVISIBLE);
		else
		{
			shareButton.setVisibility(View.VISIBLE);
			shareButton.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View arg0)
				{
					final Thread thread = new Thread()
					{
						public void run()
						{
							final Intent sendIntent = new Intent();
							sendIntent.setAction(Intent.ACTION_SEND);
							sendIntent.putExtra(Intent.EXTRA_TEXT, searchArrayList[position].shareWith());
							sendIntent.setType("text/plain");
							context.startActivity(sendIntent);
						}
					};
					thread.start();
				}
			});
		}

		//titleView.setText(searchArrayList[position].shareWith());
		//titleView.setText(Html.fromHtml(searchArrayList[position].toHTMLString()));
		titleView.setText(searchArrayList[position].toHTMLString());

		if(searchArrayList[position].tafreeg.isEmpty())
			tafreeqButton.setVisibility(View.INVISIBLE);
		else
		{
			tafreeqButton.setVisibility(View.VISIBLE);
			tafreeqButton.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View arg0)
				{
					/* No need for it. TextView is scrollable in all cases using ad.setMessage(). it was done since i was thinking textview is not scrollable
					final LayoutInflater inflater= LayoutInflater.from(context);
					final View view=inflater.inflate(R.layout.tafreeg, null);

					AppCompatTextView textview = (AppCompatTextView)view.findViewById(R.id.textview);
					textview.setText(feqhArrayList[position].tafreeg);
					*/

					final MaterialAlertDialogBuilder ad = new MaterialAlertDialogBuilder(context);
					ad.setMessage(searchArrayList[position].tafreeg)
							.setCancelable(false)
							.setTitle("تفريغ الفهرسة")
							.setPositiveButton("إغلاق", new DialogInterface.OnClickListener()
							{
								public void onClick(DialogInterface dialog, int id)
								{
									dialog.cancel();
								}
							});

					final AlertDialog alert = ad.create();
					alert.show();
				}
			});
		}
		return rowView;
	}
}