package com.maknoon.audiocataloger;

import android.content.Context;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import android.util.Log;

class DBHelper extends SQLiteOpenHelper
{
	final private static String TAG = "DBHelper";

	final static String DB_NAME = "database.db";
	final static String DB_ATTACH_NAME = "contents.db";
	//final static int DB_VERSION = BuildConfig.VERSION_CODE; // It means we will upgrade the DB with every release
	final static int DB_VERSION = 33;
	final Context context;

	DBHelper(final Context context)
	{
		super(context, DB_NAME, null, DB_VERSION);
		this.context = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		Log.v(TAG, "onCreate");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		Log.v(TAG, "onUpgrade");
	}
}