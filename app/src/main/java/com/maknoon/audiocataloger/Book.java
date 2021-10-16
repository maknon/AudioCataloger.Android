package com.maknoon.audiocataloger;

public class Book implements SheekhInterface
{
	public int id;
	public String name;
	boolean multi_volume;
	String sheekh_name;
	int sheekh_id;

	Book(int id, String name, boolean multi_volume, int sheekh_id, String sheekh_name)
	{
		this.id = id;
		this.name = name;
		this.multi_volume = multi_volume;
		this.sheekh_name = sheekh_name;
		this.sheekh_id = sheekh_id;
	}

	@Override
	public String getPath()
	{
		return null;
	}

	@Override
	public String shareWith()
	{
		return name;
	}

	@Override
	public String getTitle()
	{
		return name;
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
