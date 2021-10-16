package com.maknoon.audiocataloger;

public class FavoriteNodeInfo
{
	public final String reference;
	public final int offset;
	public final String fileName;
	public final String path;
	public final int rowid;

	FavoriteNodeInfo(String reference, int offset, String fileName, String path, int rowid)
	{
		this.reference = reference;
		this.offset = offset;
		this.fileName = fileName;
		this.path = path;
		this.rowid = rowid;
	}

	public String getTitle() {return reference;}
}