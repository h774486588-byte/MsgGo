package top.yztz.msggo.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class FileSyncManager {
    public static final String PREFS = "file_sync_prefs";
    public static final String KEY_URI = "selected_uri";
    public static final String KEY_HASH = "last_hash";
    public static final String KEY_FILE_NAME = "file_name";
    public static final String ACTION_FILE_UPDATED = "top.yztz.msggo.FILE_UPDATED";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_COUNT = "count";

    private static final String PERIODIC_NAME = "waseem_file_sync";

    private FileSyncManager() {}

    public static void rememberUri(Context context, Uri uri, String fileName) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_URI, uri.toString())
                .putString(KEY_FILE_NAME, fileName == null ? "" : fileName)
                .apply();

        schedule(context);
        syncNow(context);
    }

    public static Uri getUri(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_URI, "");
        return TextUtils.isEmpty(value) ? null : Uri.parse(value);
    }

    public static boolean isEnabled(Context context) {
        return getUri(context) != null;
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME);
    }

    public static void schedule(Context context) {
        if (getUri(context) == null) return;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                FileSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic);
    }

    public static void syncNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FileSyncWorker.class)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "waseem_file_sync_now",
                ExistingWorkPolicy.REPLACE,
                request);
    }

    public static void notifyUi(Context context, String path, int count) {
        Intent intent = new Intent(ACTION_FILE_UPDATED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_PATH, path);
        intent.putExtra(EXTRA_COUNT, count);
        context.sendBroadcast(intent);
    }
}
