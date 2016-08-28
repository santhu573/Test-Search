package com.example.here_search;


import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper  {
	static final String TABLE_HISTORY = "history";
	private static final int DATABASE_VERSION = 1;
	static final String KEY_ID = "id";
	private static final String KEY_LABEL = "label";
	private static final String KEY_VALUE = "value";
	
	static Context ctx;
	public Database(Context context, String ProjectName) {
		super(context, ProjectName, null, DATABASE_VERSION);
		Database.ctx=context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
	}
	
	public void createprojecttable(){
		try{	
		SQLiteDatabase db = this.getWritableDatabase();
		String CREATE_HISTORY_TABLE = "CREATE TABLE " + TABLE_HISTORY + "("
				+ KEY_ID + " INTEGER PRIMARY KEY,"+ KEY_LABEL + " TEXT,"
				+ KEY_VALUE + " TEXT" + ")";
		db.execSQL(CREATE_HISTORY_TABLE);
		}catch(Throwable e){
			
		}
	}
	
	void addValue(String table, String label, String value) {
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(KEY_LABEL, label); 
		values.put(KEY_VALUE, value); 
		db.insert(table, null, values);
	}

}
