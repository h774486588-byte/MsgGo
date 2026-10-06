package top.yztz.msggo.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import top.yztz.msggo.R;
import top.yztz.msggo.util.SpreadsheetReader;

public class FileSyncWorker extends Worker {
    private static final String CHANNEL = "file_sync_channel";
    private static final int NOTIFICATION_ID = 24013;

    public FileSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Uri uri = FileSyncManager.getUri(context);
        if (uri == null) return Result.success();

        try {
            File target = getTargetFile(context, uri);
            String newHash = copyAndHash(context, uri, target);
            if (newHash.isEmpty()) return Result.retry();

            String oldHash = context.getSharedPreferences(
                    FileSyncManager.PREFS, Context.MODE_PRIVATE)
                    .getString(FileSyncManager.KEY_HASH, "");

            if (newHash.equals(oldHash)) {
                return Result.success();
            }

            // Read the updated spreadsheet and count only genuinely new rows.
            SpreadsheetReader reader = new SpreadsheetReader();
            reader.read(target.getAbsolutePath());
            java.util.List<java.util.HashMap<String, String>> rows = reader.readContent();
            String scope = SentMessageStore.scopeKey(context, target.getAbsolutePath());
            int newCount = SentMessageStore.countNewRows(
                    context, rows, reader.getTitles(), scope);

            context.getSharedPreferences(FileSyncManager.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(FileSyncManager.KEY_HASH, newHash)
                    .apply();

            if (newCount > 0) {
                showUpdatedNotification(context, target.getName(), newCount);
            }
            // Always refresh the visible file after a real source change. The
            // DataModel queue preparation will hide already-sent rows.
            FileSyncManager.notifyUi(context, target.getAbsolutePath(), newCount);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private File getTargetFile(Context context, Uri uri) {
        String name = context.getSharedPreferences(FileSyncManager.PREFS, Context.MODE_PRIVATE)
                .getString(FileSyncManager.KEY_FILE_NAME, "");
        if (name == null || name.trim().isEmpty()) {
            name = queryDisplayName(context, uri);
        }
        if (name == null || name.trim().isEmpty()) name = "synced.xlsx";
        File dir = new File(context.getFilesDir(), "synced");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, name);
    }

    private String queryDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String copyAndHash(Context context, Uri uri, File target) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) return "";
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        byte[] bytes = digest.digest();
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format("%02x", b));
        return result.toString();
    }

    private void showUpdatedNotification(Context context, String fileName, int count) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "File synchronization",
                NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(channel);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_table)
                .setContentTitle(context.getString(R.string.sync_ready_title))
                .setContentText(context.getString(R.string.sync_ready_count, count))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.sync_ready_detail, fileName, count)))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
