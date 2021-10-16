package com.maknoon.audiocataloger;

public class Sheekh implements SheekhInterface
{
	public int id;
	public String name;

	Sheekh(int id, String name)
	{
		this.id = id;
		this.name = name;
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
