package com.maknoon.audiocataloger;

public class Sub_Book implements SheekhInterface
{
	int book_id;
	String book;
	boolean multi_volume; // true in all cases. no need remove it
	String sheekh_name;
	int sheekh_id;
	public String title;

	Sub_Book(int book_id, String book, boolean multi_volume, int sheekh_id, String sheekh_name, String title)
	{
		this.book_id = book_id;
		this.book = book;
		this.multi_volume = multi_volume;
		this.sheekh_name = sheekh_name;
		this.sheekh_id = sheekh_id;
		this.title = title;
	}

	@Override
	public String getPath()
	{
		return null;
	}

	@Override
	public String shareWith()
	{
		return title;
	}

	@Override
	public String getTitle()
	{
		return title;
	}

	@Override
	public String getTafreeg()
	{
		return null;
	}

	@Override
	public boolean isChapter()
	{
		return false;
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
	}
}