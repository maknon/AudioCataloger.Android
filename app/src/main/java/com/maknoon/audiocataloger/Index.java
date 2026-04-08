package com.maknoon.audiocataloger;

import static com.maknoon.audiocataloger.MainActivity.toURL;

public class Index implements SheekhInterface
{
	int code;
	String line;
	public String tafreeg;
	public int offset;
	public int seq;
	public int duration;

	int book_id;
	boolean multi_volume;
	String sheekh_name;
	int sheekh_id;
	String sub_book;
	String book;
	public String fileName;
	public String path;

	// Version 1.3
	public String title;

	final String HTMLString; // Version 4

	Index(int code, String line, String tafreeg, int offset, int seq, int duration, int book_id, boolean multi_volume, String sub_book, String sheekh_name, String book, int sheekh_id, String fileName, String path, String title)
	{
		this.code = code;
		this.line = line;
		this.tafreeg = tafreeg;
		this.offset = offset;
		this.seq = seq;
		this.duration = duration;

		this.book_id = book_id;
		this.multi_volume = multi_volume;
		this.sub_book = sub_book;
		this.sheekh_id = sheekh_id;
		this.sheekh_name = sheekh_name;
		this.book = book;
		this.fileName = fileName;
		this.path = path;

		this.title = title;

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

		HTMLString = dur + line;
	}

	@Override
	public String getPath()
	{
		return path + "/" + fileName + ".m4a";
	}

	@Override
	public String shareWith()
	{
		final String hour = String.valueOf(offset / 3600 / 1000);
		final String minute = String.valueOf(offset / 60 / 1000 - (offset / 3600 / 1000) * 60);
		final String second = String.valueOf(offset / 1000 - ((int) ((float) offset / 60F / 1000F) * 60));
		final String offset = "[" + hour + ":" + minute + ":" + second + "]";

		if (book.equals(title))
			return line + "\n" + sheekh_name + "←" + book + "←" + fileName + "\n" + toURL(path, fileName, seq, code, true) + "\n" + "أو يمكن الاستماع إلى الشريط كاملا (الفتوى تبدأ من " + offset + ")" + "\n" + toURL(path, fileName, code, true) + "\n" + "برنامج مفهرس المحاضرات. للتحميل:" + "\n" + "https://fiqh.cc/?app";
		else
			return line + "\n" + sheekh_name + "←" + book + "←" + title + "←" + fileName + "\n" + toURL(path, fileName, seq, code,true) + "\n" + "أو يمكن الاستماع إلى الشريط كاملا (الفتوى تبدأ من " + offset + ")" + "\n" + toURL(path, fileName, code, true) + "\n" + "برنامج مفهرس المحاضرات. للتحميل:" + "\n" + "https://fiqh.cc/?app";
	}

	@Override
	public String getTitle()
	{
		return HTMLString;
	}

	@Override
	public String getTafreeg()
	{
		return tafreeg.isEmpty() ? null : tafreeg;
	}

	@Override
	public boolean isChapter()
	{
		return false;
	}

	@Override
	public boolean isIndex()
	{
		return true;
	}

	@Override
	public boolean shareWithDisabled()
	{
		return duration == 0;
	}

	@Override
	public void playChapter()
	{
	}
}