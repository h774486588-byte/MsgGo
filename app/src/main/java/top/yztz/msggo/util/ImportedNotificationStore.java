package top.yztz.msggo.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Durable index of imported spreadsheet rows, separate from the send queue. */
public final class ImportedNotificationStore extends SQLiteOpenHelper {
    private static final String DB = "waseem_imported_notifications.db";
    private static final String TABLE = "imported_notifications";
    public static final String PENDING = "pending", SENT = "sent", FAILED = "failed", ARCHIVED = "archived";

    public static final class Item {
        public String key, phone, message, eventDate, source, status;
        public long importedAt, updatedAt;
    }
    public static final class Summary { public int added, duplicates, existing; }

    public ImportedNotificationStore(Context context) { super(context.getApplicationContext(), DB, null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (message_key TEXT PRIMARY KEY, phone TEXT NOT NULL, message TEXT NOT NULL, event_date TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'pending', imported_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX imported_date_status_idx ON " + TABLE + "(event_date,status)");
        db.execSQL("CREATE INDEX imported_phone_idx ON " + TABLE + "(phone)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    /** Upsert all spreadsheet rows atomically; keeps existing delivery status. */
    public synchronized Summary importRows(List<HashMap<String,String>> rows, String phoneColumn, String messageColumn, String dateColumn, String source) {
        Summary s = new Summary();
        if (rows == null || TextUtils.isEmpty(phoneColumn) || TextUtils.isEmpty(messageColumn)) return s;
        SQLiteDatabase db = getWritableDatabase(); long now = System.currentTimeMillis(); Set<String> seen = new HashSet<>();
        db.beginTransaction();
        try {
            for (HashMap<String,String> row : rows) {
                if (row == null) continue;
                String phone = PhoneNumberUtil.fromSpreadsheet(row.get(phoneColumn));
                String message = row.get(messageColumn);
                if (TextUtils.isEmpty(phone) || TextUtils.isEmpty(message) || message.trim().isEmpty()) continue;
                String date = dateColumn == null ? "" : clean(row.get(dateColumn));
                String src = source == null ? "" : source;
                String key = digest(src + "\u0001" + phone + "\u0001" + date + "\u0001" + message);
                if (!seen.add(key)) { s.duplicates++; continue; }
                ContentValues values = new ContentValues(); values.put("message_key", key); values.put("phone", phone); values.put("message", message); values.put("event_date", date); values.put("source", src); values.put("updated_at", now);
                try (Cursor c = db.query(TABLE, new String[]{"message_key"}, "message_key=?", new String[]{key}, null, null, null)) {
                    if (c.moveToFirst()) { db.update(TABLE, values, "message_key=?", new String[]{key}); s.existing++; }
                    else { values.put("status", PENDING); values.put("imported_at", now); db.insertOrThrow(TABLE, null, values); s.added++; }
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return s;
    }

    public synchronized boolean setStatus(String key, String status) {
        if (TextUtils.isEmpty(key) || !(PENDING.equals(status)||SENT.equals(status)||FAILED.equals(status)||ARCHIVED.equals(status))) return false;
        ContentValues v = new ContentValues(); v.put("status",status); v.put("updated_at",System.currentTimeMillis());
        return getWritableDatabase().update(TABLE,v,"message_key=?",new String[]{key}) > 0;
    }

    public synchronized List<Item> search(String phone, String from, String to, String text, String status) {
        List<Item> out = new ArrayList<>(); StringBuilder where = new StringBuilder("1=1"); List<String> args = new ArrayList<>();
        if (!TextUtils.isEmpty(phone)) { where.append(" AND phone LIKE ?"); args.add("%"+PhoneNumberUtil.fromSpreadsheet(phone.trim())+"%"); }
        if (!TextUtils.isEmpty(from)) { where.append(" AND event_date >= ?"); args.add(from.trim()); }
        if (!TextUtils.isEmpty(to)) { where.append(" AND event_date <= ?"); args.add(to.trim()); }
        if (!TextUtils.isEmpty(text)) { where.append(" AND message LIKE ?"); args.add("%"+text.trim()+"%"); }
        if (!TextUtils.isEmpty(status)) { where.append(" AND status=?"); args.add(status); }
        try (Cursor c=getReadableDatabase().query(TABLE,new String[]{"message_key","phone","message","event_date","source","status","imported_at","updated_at"},where.toString(),args.toArray(new String[0]),null,null,"event_date DESC, imported_at DESC")) {
            while(c.moveToNext()) { Item i=new Item(); i.key=c.getString(0); i.phone=c.getString(1); i.message=c.getString(2); i.eventDate=c.getString(3); i.source=c.getString(4); i.status=c.getString(5); i.importedAt=c.getLong(6); i.updatedAt=c.getLong(7); out.add(i); }
        }
        return out;
    }
    private static String clean(String v) { return v == null ? "" : v.trim(); }
    private static String digest(String value) {
        try { byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte x:b) s.append(String.format(Locale.ROOT,"%02x",x)); return s.toString(); }
        catch(Exception e) { return Integer.toHexString(value.hashCode()); }
    }
}
