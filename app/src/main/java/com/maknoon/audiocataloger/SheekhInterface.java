package com.maknoon.audiocataloger;

public interface SheekhInterface
{
	String getTitle();
	String shareWith();
	String getTafreeg();
	String getPath();
	boolean isChapter();
	boolean isIndex();
	boolean shareWithDisabled();
	void playChapter();
}