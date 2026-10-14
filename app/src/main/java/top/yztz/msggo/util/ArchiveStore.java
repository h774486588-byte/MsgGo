package top.yztz.msggo.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Permanent local archive for SMS notifications confirmed as sent.
 * Records are never removed when the source spreadsheet is refreshed.
 */
public final class ArchiveStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "waseem_notifications.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "sent_archive";

    public static final class Record {
        public long id;
        public String phone;
        public String content;
        public String eventDate;
        public long sentAt;
        public String source;
    }

    public ArchiveStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "phone TEXT NOT NULL,"
                + "content TEXT NOT NULL,"
                + "event_date TEXT NOT NULL DEFAULT '',"
                + "sent_at INTEGER NOT NULL,"
                + "source TEXT NOT NULL DEFAULT '',"
                + "UNIQUE(phone, content, event_date, source))");
        db.execSQL("CREATE INDEX idx_archive_phone_date ON " + TABLE + "(phone, event_date)");
        db.execSQL("CREATE INDEX idx_archive_sent_at ON " + TABLE + "(sent_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep the archive intact when adding future schema versions.
    }

    public synchronized void archiveSent(String phone, String content, String eventDate, String source) {
        if (TextUtils.isEmpty(phone) || content == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("phone", PhoneNumberUtil.fromSpreadsheet(phone));
        values.put("content", content);
        values.put("event_date", eventDate == null ? "" : eventDate);
        values.put("sent_at", System.currentTimeMillis());
        values.put("source", source == null ? "" : source);
        db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized List<Record> search(String phone, String fromDate, String toDate, String text) {
        List<Record> result = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        String normalizedPhone = phone == null ? "" : PhoneNumberUtil.fromSpreadsheet(phone).trim();
        if (!normalizedPhone.isEmpty()) {
            where.append(" AND phone LIKE ?");
            args.add("%" + normalizedPhone + "%");
        }
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            where.append(" AND event_date >= ?");
            args.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            where.append(" AND event_date <= ?");
            args.add(toDate.trim());
        }
        if (text != null && !text.trim().isEmpty()) {
            where.append(" AND content LIKE ?");
            args.add("%" + text.trim() + "%");
        }
        try (Cursor c = getReadableDatabase().query(TABLE,
                new String[]{"id","phone","content","event_date","sent_at","source"},
                where.toString(), args.toArray(new String[0]), null, null,
                "event_date DESC, sent_at DESC")) {
            while (c.moveToNext()) {
                Record r = new Record();
                r.id = c.getLong(0);
                r.phone = c.getString(1);
                r.content = c.getString(2);
                r.eventDate = c.getString(3);
                r.sentAt = c.getLong(4);
                r.source = c.getString(5);
                result.add(r);
            }
        }
        return result;
    }

    public synchronized int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public static String formatSentAt(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(millis));
    }
}