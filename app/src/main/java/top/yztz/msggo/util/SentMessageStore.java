package top.yztz.msggo.util;

import android.content.Context;
import android.text.TextUtils;

import top.yztz.msggo.data.DataModel;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Persistent registry of messages that were actually confirmed as sent.
 *
 * The registry is scoped to the selected source file (URI when a synced
 * document is selected, otherwise the local path). A message is identified
 * by the normalized recipient and the exact final message text.
 */
public final class SentMessageStore {
    private static final String PREFS = "sent_message_store";
    private static final String PREFIX = "sent_";

    private SentMessageStore() {}

    public static String scopeKey(Context context, String fallbackPath) {
        android.net.Uri uri = FileSyncManager.getUri(context);
        if (uri != null) return "uri:" + uri.toString();
        return "path:" + (fallbackPath == null ? "" : fallbackPath);
    }

    public static boolean isSent(Context context, String scope, String phone, String message) {
        String key = buildStorageKey(scope, phone, message);
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false);
    }

    public static void markSent(Context context, String scope, String phone, String message) {
        if (TextUtils.isEmpty(phone) || message == null) return;
        String key = buildStorageKey(scope, phone, message);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, true)
                .apply();
    }

    public static String buildMessageKey(String phone, String message) {
        String normalizedPhone = PhoneNumberUtil.fromSpreadsheet(phone);
        return normalizedPhone + "\u0000" + (message == null ? "" : message);
    }

    private static String buildStorageKey(String scope, String phone, String message) {
        return PREFIX + sha256((scope == null ? "" : scope) + "\u0001"
                + buildMessageKey(phone, message));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * Counts only genuinely new rows. Duplicate recipient+message pairs are
     * counted once, matching DataModel.deduplicate().
     */
    public static int countNewRows(Context context,
                                   List<HashMap<String, String>> rows,
                                   String[] titles,
                                   String scope) {
        if (rows == null || rows.isEmpty() || titles == null) return 0;

        String numberColumn = findNumberColumn(rows, titles);
        String messageColumn = findMessageColumn(titles);
        if (TextUtils.isEmpty(numberColumn) || TextUtils.isEmpty(messageColumn)) return 0;

        Set<String> seen = new HashSet<>();
        int count = 0;
        for (HashMap<String, String> row : rows) {
            // Notifications count only new operations whose date is today.
            if (!DataModel.isTodayRow(row, titles)) continue;
            String phone = PhoneNumberUtil.normalizeMapValue(row, numberColumn);
            if (TextUtils.isEmpty(phone)) continue;
            String message = row.get(messageColumn);
            if (message == null) message = "";
            String key = buildMessageKey(phone, message);
            if (!seen.add(key)) continue;
            if (!isSent(context, scope, phone, message)) count++;
        }
        return count;
    }

    private static String findMessageColumn(String[] titles) {
        for (String title : titles) {
            String n = normalizeHeader(title);
            if ("الرسالة".equals(n) || "message".equals(n) || "sms".equals(n)
                    || "نص الرسالة".equals(n) || "محتوى الرسالة".equals(n)) {
                return title;
            }
        }
        return "";
    }

    private static String findNumberColumn(List<HashMap<String, String>> rows, String[] titles) {
        for (String title : titles) {
            String n = normalizeHeader(title);
            if ("المستلم".equals(n) || "recipient".equals(n) || "receiver".equals(n)
                    || "phone".equals(n) || "mobile".equals(n)
                    || "رقم الهاتف".equals(n) || "رقم الجوال".equals(n)
                    || "الهاتف".equals(n) || "الجوال".equals(n)) {
                return title;
            }
        }

        int bestCount = 0;
        String bestTitle = "";
        for (String title : titles) {
            String n = normalizeHeader(title);
            if (n.matches(".*(movement|transaction|user|device|result|رقم الحركة|الحركة|المستخدم|الجهاز|النتيجة).*")) {
                continue;
            }
            int count = 0;
            for (HashMap<String, String> row : rows) {
                if (PhoneNumberUtil.isPlausible(row.get(title))) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                bestTitle = title;
            }
        }
        return bestTitle;
    }

    private static String normalizeHeader(String title) {
        if (title == null) return "";
        return title.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", " ");
    }
}
