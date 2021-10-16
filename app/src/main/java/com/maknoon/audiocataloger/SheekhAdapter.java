package com.maknoon.audiocataloger;

import java.io.File;
import java.util.Arrays;

import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

class SheekhAdapter<T extends SheekhInterface> extends ArrayAdapter<T>
{
	private static final String TAG = "SheekhAdapter";

	private final Context context;
	private final T[] sheekhArrayList;

	SheekhAdapter(Context context, T[] sheekhArrayList)
	{
		super(context, R.layout.sheekh_listview, Arrays.asList(sheekhArrayList));

		this.context = context;
		this.sheekhArrayList = sheekhArrayList;
	}

	@Override
	@NonNull
	public View getView(final int position, View convertView, @NonNull ViewGroup parent)
	{
		View rowView = convertView;

		if (rowView == null)
		{
			final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rowView = inflater.inflate(R.layout.sheekh_listview, parent, false);
		}

		final AppCompatImageView iconView = rowView.findViewById(R.id.item_icon);
		final AppCompatTextView titleView = rowView.findViewById(R.id.item_title);
		final AppCompatImageButton listenButton = rowView.findViewById(R.id.listen);
		final AppCompatImageButton tafreeqButton = rowView.findViewById(R.id.tafreeg);
		final AppCompatImageButton shareButton = rowView.findViewById(R.id.share);
		final AppCompatImageButton downloadButton = rowView.findViewById(R.id.download);

		titleView.setText(sheekhArrayList[position].getTitle());

		if (sheekhArrayList[position].isIndex())
		{
			iconView.setVisibility(View.GONE); // View.GONE to free the space for the text
			listenButton.setVisibility(View.INVISIBLE);
			downloadButton.setVisibility(View.INVISIBLE);

			if (sheekhArrayList[position].shareWithDisabled())
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
								sendIntent.putExtra(Intent.EXTRA_TEXT, sheekhArrayList[position].shareWith());
								sendIntent.setType("text/plain");
								context.startActivity(sendIntent);
							}
						};
						thread.start();
					}
				});
			}

			if (sheekhArrayList[position].getTafreeg() != null)
			{
				tafreeqButton.setVisibility(View.VISIBLE);
				tafreeqButton.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View arg0)
					{
						final AlertDialog.Builder ad = new AlertDialog.Builder(context);
						ad
								.setMessage(sheekhArrayList[position].getTafreeg())
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
			else
				tafreeqButton.setVisibility(View.INVISIBLE);
		}
		else
		{
			if (sheekhArrayList[position].isChapter())
			{
				listenButton.setVisibility(View.VISIBLE);
				listenButton.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View arg0)
					{
						sheekhArrayList[position].playChapter();
					}
				});

				if (MainActivity.sdPath == null)
					Log.e(TAG, "App does not have any place to put its files !");
				else
				{
					final File f = new File(MainActivity.sdPath + sheekhArrayList[position].getPath());
					if (f.exists())
					{
						downloadButton.setImageResource(R.drawable.baseline_done_24);
						downloadButton.setEnabled(false);
					}
					else
					{
						downloadButton.setImageResource(R.drawable.baseline_save_alt_24);
						downloadButton.setEnabled(true);
					}

					downloadButton.setVisibility(View.VISIBLE);
					downloadButton.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View arg0)
						{
							final DownloadManager.Request request = new DownloadManager.Request(MainActivity.toUri(sheekhArrayList[position].getPath()));

							// only download via WIFI
							//request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);

							request.setDescription(sheekhArrayList[position].shareWith());
							request.setTitle("برنامج المفهرس - تحميل الشريط");
							request.allowScanningByMediaScanner();
							request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

							System.out.println("MainActivity.sdPath: " + MainActivity.sdPath);

							// DownloadManager cannot override the same file.
							//if(MainActivity.isSDPresent) no need since download is enabled only when isSDPresent=true
							final File f = new File(MainActivity.sdPath + sheekhArrayList[position].getPath() + ".part");

							if (f.exists())
								System.out.println("f.delete(): " + f.delete());
							else
								System.out.println("f.getParentFile().mkdirs(): " + f.getParentFile().mkdirs());

							//request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, MainActivity.audiosFolderName + "/" + sheekhArrayList[position].getPath()+".part"); // Version 7, replace 'com.maknoon.audiocataloger' with مفهرس المحاضرات
							request.setDestinationUri(Uri.fromFile(f)); // Version 7.2, to allow choosing different director based on user choice.

							downloadButton.setImageResource(R.drawable.baseline_hourglass_empty_24);
							downloadButton.setEnabled(false);
							downloadButton.invalidate();

							((MainActivity) context).downloadAudioFile(request); // TODO, replace with a broadcast to the mainactivity and initiate this from the MainActivity
						}
					});
				}
			}
			else
			{
				listenButton.setVisibility(View.INVISIBLE);
				downloadButton.setVisibility(View.INVISIBLE);
			}

			tafreeqButton.setVisibility(View.INVISIBLE);
			shareButton.setVisibility(View.INVISIBLE);
		}

		return rowView;
	}
}