package com.maknoon.audiocataloger;

import android.content.Context;

import com.google.android.material.color.MaterialColors;

public class FeqhNodeInfo
{
	public final int category_id;
	public final int category_parent;
	public final boolean isIndex;
	public final String category_name;

	public final int code;
	public final int offset;
	public final int duration;
	public final int seq;
	public String line;
	public final String tafreeg;

	public final String sheekh_name;
	public final String book_name;
	public final String title;
	public final String fileName;
	public final String path;

	public final String HTMLString; // Version 4

	FeqhNodeInfo(Context ctx, boolean isIndex, int Category_id, String Category_name, int Category_parent, int code, int offset, int duration, int seq, String line, String tafreeg, String sheekh_name, String book_name, String title, String fileName, String path)
	{
		// For category
		this.isIndex = isIndex;
		this.category_id = Category_id;
		this.category_name = Category_name;
		this.category_parent = Category_parent;

		// For index
		this.code = code;
		this.offset = offset;
		this.duration = duration;
		this.seq = seq;
		this.line = line;
		this.tafreeg = tafreeg;

		// For toHTMLString() and index playerSrv.play
		this.sheekh_name = sheekh_name;
		this.book_name = book_name;
		this.title = title;
		this.fileName = fileName;
		this.path = path;

		// Version 4, toHTMLString is called *many* times while refreshing the view, do all the calculation here to have it once.
		final String dur;
		if (duration == -1)
			dur = "[?] ";
		else
		{
			final String minute = String.valueOf(duration / 60 / 1000);
			final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
			dur = "[" + minute + ':' + second + "] ";
		}

		final int c = MaterialColors.getColor(ctx, R.attr.colorPrimaryDark, ctx.getResources().getColor(android.R.color.holo_red_light));
		if (isIndex && book_name.equals(title))
			HTMLString = dur + line + " <font color='" + String.format("#%06X", (0xFFFFFF & c)) + "'>" + sheekh_name + "←" + book_name + "←" + fileName + "</font>";
		else
			HTMLString = dur + line + " <font color='" + String.format("#%06X", (0xFFFFFF & c)) + "'>" + sheekh_name + "←" + book_name + "←" + title + "←" + fileName + "</font>";
	}

	public String shareWith()
	{
		if (book_name.equals(title))
			return line + "\n" + sheekh_name + "←" + book_name + "←" + fileName;
		else
			return line + "\n" + sheekh_name + "←" + book_name + "←" + title + "←" + fileName;
	}

	public String toHTMLString()
	{
		if (isIndex)
			return HTMLString;
		else
			return category_name;
	}
}