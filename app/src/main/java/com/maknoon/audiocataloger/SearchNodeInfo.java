package com.maknoon.audiocataloger;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

class SearchNodeInfo
{
	public String line;
	public final int offset;
	public final int seq;
	public final int duration;
	public final int code;
	public final String sheekh_name;
	public final String book_name;
	public final String title;
	public final String fileName;
	public final String path;
	public final String input;
	public final String tafreeg;

	public final Spannable HTMLString; // Version 4

	SearchNodeInfo(Context ctx, String line, int offset, int duration, String sheekh_name, String book_name, String title, String fileName, String path, String input, String tafreeg, int seq, int code)
	{
		this.line = line;
		this.offset = offset;
		this.duration = duration;
		this.sheekh_name = sheekh_name;
		this.book_name = book_name;
		this.title = title;
		this.fileName = fileName;
		this.path = path;
		this.input = input;
		this.tafreeg = tafreeg;
		this.seq = seq;
		this.code = code;

		final String ref;
		if (duration == -1)
			ref = "[?] " + line;
		else
		{
			final String minute = String.valueOf(duration / 60 / 1000);
			final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
			ref = "[" + minute + ":" + second + "] " + line;
		}

		// Same performance of HTML
		// Version 4, toHTMLString is called *many* times while refreshing the view, do all the calculation here to have it once.
		// TODO: Offsets Function in FTS4 can be used to get the location of terms
		if (book_name.equals(title))
			HTMLString = new SpannableString(ref + "  " + sheekh_name + "←" + book_name + "←" + fileName);
		else
			HTMLString = new SpannableString(ref + "  " + sheekh_name + "←" + book_name + "←" + title + "←" + fileName);

		final TypedValue typedValue = new TypedValue();
		ctx.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
		final int colorPrimary = ContextCompat.getColor(ctx, typedValue.resourceId);

		final String[] search = input.split(" ");
		for (String term : search)
		{
			int index = ref.indexOf(term);
			while (index >= 0)
			{
				//HTMLString.setSpan(new ForegroundColorSpan(Color.parseColor("#800000")), index, index + term.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				HTMLString.setSpan(new ForegroundColorSpan(colorPrimary), index, index + term.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				index = ref.indexOf(term, index + 1);
			}
		}

		HTMLString.setSpan(new ForegroundColorSpan(colorPrimary), ref.length(), HTMLString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	}

	public String shareWith()
	{
		final String hour = String.valueOf(offset / 3600 / 1000);
		final String minute = String.valueOf(offset / 60 / 1000 - (offset / 3600 / 1000) * 60);
		final String second = String.valueOf(offset / 1000 - ((int) ((float) offset / 60F / 1000F) * 60));
		final String offset = "[" + hour + ":" + minute + ":" + second + "]";

		if (book_name.equals(title))
			return line + "\n" + sheekh_name + "←" + book_name + "←" + fileName + "\n" + MainActivity.toURL(path, fileName, seq, code, true) + "\n" + "أو يمكن الاستماع إلى الشريط كاملا (الفتوى تبدأ من " + offset + ")" + "\n" + MainActivity.toURL(path, fileName, code, true) + "\n" + "برنامج مفهرس المحاضرات. للتحميل:" + "\n" + "https://fiqh.cc/?app";
		else
			return line + "\n" + sheekh_name + "←" + book_name + "←" + title + "←" + fileName + "\n" + MainActivity.toURL(path, fileName, seq, code, true) + "\n" + "أو يمكن الاستماع إلى الشريط كاملا (الفتوى تبدأ من " + offset + ")" + "\n" + MainActivity.toURL(path, fileName, code, true) + "\n" + "برنامج مفهرس المحاضرات. للتحميل:" + "\n" + "https://fiqh.cc/?app";
	}

	public Spannable toHTMLString()
	{
		return HTMLString;
		//return line+" <font color='maroon'>"+sheekh_name+"←"+book_name+"←"+title+"←"+fileName+"</font>";
	}
}