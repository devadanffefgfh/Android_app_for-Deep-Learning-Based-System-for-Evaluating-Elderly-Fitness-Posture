package com.edu.wzu.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.net.Uri;
public class MyDatabaseHelper extends SQLiteOpenHelper {
    public static final String CREATE_MEDIA = "CREATE TABLE Media (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "type TEXT," + // 新增類型欄位來區分是圖片還是影片
            "uri TEXT)";

    private Context mContext;

    public MyDatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        //mContext = context;
        this.mContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_MEDIA);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS Media");
        onCreate(db);
    }

    // 新增存儲 URI 的方法，包括類型參數
    public void storeUri(String type, String uri) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("type", type);
        values.put("uri", uri);
        db.insert("Media", null, values);
        db.close();
    }

    // 删除指定 URI 的方法
    public void deleteAllUris() {
        SQLiteDatabase db = this.getWritableDatabase();

        // 取得所有 URI
        List<String> uris = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT uri FROM Media", null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                uris.add(cursor.getString(0));
            }
            cursor.close();
        }

        // 刪除影片檔案
        for (String uriString : uris) {
            try {
                Uri uri = Uri.parse(uriString);
                if ("content".equals(uri.getScheme())) {
                    ContentResolver contentResolver = mContext.getContentResolver();
                    int deletedRows = contentResolver.delete(uri, null, null);
                    if (deletedRows > 0) {
                        Log.d("MyDatabaseHelper", "成功刪除影片: " + uriString);
                    } else {
                        Log.e("MyDatabaseHelper", "刪除影片失敗: " + uriString);
                    }
                } else {
                    File file = new File(uriString);
                    if (file.exists() && file.delete()) {
                        Log.d("MyDatabaseHelper", "檔案刪除成功: " + uriString);
                    } else {
                        Log.e("MyDatabaseHelper", "檔案刪除失敗或不存在: " + uriString);
                    }
                }
            } catch (Exception e) {
                Log.e("MyDatabaseHelper", "刪除影片時發生錯誤: " + e.getMessage());
            }
        }

        // 刪除資料庫中的所有記錄
        db.delete("Media", null, null);
        db.close();
    }



    // 存储 URI 到数据库，并指定 type
    public long insertMedia(String uri, String type) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uri", uri);
        values.put("type", type);  // 存储 type
        long id = db.insert("Media", null, values);
        db.close();
        return id;
    }
}
