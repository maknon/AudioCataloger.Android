package com.maknoon.audiocataloger;

import java.util.Arrays;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import static com.maknoon.audiocataloger.MainActivity.Urlshortener_firebase;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class FeqhAdapter extends ArrayAdapter<FeqhNodeInfo>
{
	private final Context context;
	private final FeqhNodeInfo[] feqhArrayList;

	FeqhAdapter(Context context, FeqhNodeInfo[] feqhArrayList)
	{
		super(context, R.layout.fegh_listiew, Arrays.asList(feqhArrayList));

		this.context = context;
		this.feqhArrayList = feqhArrayList;
	}

	@Override
	@NonNull
	public View getView(final int position, View convertView, @NonNull ViewGroup parent)
	{
		final View rowView;

		if(convertView == null)
		{
			final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.fegh_listiew, parent, false);
		}
		else
			rowView = convertView;

		final AppCompatImageView iconView = rowView.findViewById(R.id.item_icon);
		final AppCompatTextView titleView = rowView.findViewById(R.id.item_title);
		final AppCompatImageButton tafreeqButton = rowView.findViewById(R.id.tafreeg);
		final AppCompatImageButton shareButton = rowView.findViewById(R.id.share);

		//titleView.setText(feqhArrayList[position].shareWith());
		titleView.setText(Html.fromHtml(feqhArrayList[position].toHTMLString()));

		if(feqhArrayList[position].isIndex)
		{
			iconView.setVisibility(View.GONE); // View.GONE to free the space for the text
			tafreeqButton.setVisibility(View.VISIBLE);

			if(feqhArrayList[position].duration == 0)
				shareButton.setVisibility(View.INVISIBLE);
			else
			{
				shareButton.setVisibility(View.VISIBLE);
				shareButton.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View arg0)
					{
						final String hour = String.valueOf(feqhArrayList[position].offset / 3600 / 1000);
						final String minute = String.valueOf(feqhArrayList[position].offset / 60 / 1000 - (feqhArrayList[position].offset / 3600 / 1000) * 60);
						final String second = String.valueOf(feqhArrayList[position].offset / 1000 - ((int) ((float) feqhArrayList[position].offset / 60F / 1000F) * 60));
						final String offset = "[" + hour + ":" + minute + ":" + second + "]";

						final Thread thread = new Thread()
						{
							public void run()
							{
								final Intent sendIntent = new Intent();
								sendIntent.setAction(Intent.ACTION_SEND);
								sendIntent.putExtra(Intent.EXTRA_TEXT, feqhArrayList[position].shareWith() + "\n" + MainActivity.toURL(feqhArrayList[position].path, feqhArrayList[position].fileName, feqhArrayList[position].seq, true) + "\n" + "أو يمكن الاستماع إلى الشريط كاملا (الفتوى تبدأ من " + offset + ")" + "\n" + MainActivity.toURL(feqhArrayList[position].path, feqhArrayList[position].fileName, true) + "\n" + "برنامج مفهرس المحاضرات. للتحميل:" + "\n" + Urlshortener_firebase("https://play.google.com/store/apps/details?id=com.maknoon.audiocataloger"));
								sendIntent.setType("text/plain");
								context.startActivity(sendIntent);
							}
						};
						thread.start();
					}
				});
			}

			/* This code is not working properly. the layout sometime refresh in the original layout.
			// Remove the rule for layout_toEndOf so that the index can take the whole cell width
			final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) titleView.getLayoutParams();
			lp.removeRule(RelativeLayout.END_OF);
			lp.setMarginStart(2);
			titleView.setLayoutParams(lp);
			*/
			//iconView.setImageResource(R.drawable.ic_play_arrow_black_24dp);

			if(feqhArrayList[position].tafreeg.isEmpty())
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
						ad.setMessage(feqhArrayList[position].tafreeg)
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
		}
		else
		{
			iconView.setVisibility(View.VISIBLE);
			tafreeqButton.setVisibility(View.INVISIBLE);
			shareButton.setVisibility(View.INVISIBLE);
			iconView.setImageResource(R.drawable.baseline_folder_open_24);
		}

		return rowView;
	}
}