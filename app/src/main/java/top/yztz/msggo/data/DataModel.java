package top.yztz.msggo.data;

import android.content.Context;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import top.yztz.msggo.exception.DataLoadFailed;
import top.yztz.msggo.services.SMSSender;
import top.yztz.msggo.util.HashUtils;
import top.yztz.msggo.util.PhoneNumberUtil;
import top.yztz.msggo.util.SpreadsheetReader;
import top.yztz.msggo.util.TextParser;

public class DataModel implements Serializable {
    private static String[] titles = null;
    private static String path;
    private static long timestamp;
    private static String template;
    private static String numberColumn;
    private static String signature;
    private static int subId;
    private static List<HashMap<String, String>> data = null;
    private static List<Integer> sourceRowNumbers = null;
    private static boolean loaded = false;

    public static boolean loaded() {
        return loaded;
    }

    public static synchronized void load(String path) throws DataLoadFailed {
        SpreadsheetReader reader = new SpreadsheetReader();
        reader.read(path);

        DataModel.data = reader.readContent();
        DataModel.sourceRowNumbers = reader.getSourceRowNumbers();
        DataModel.titles = reader.getTitles();
        DataModel.path = path;
        DataModel.signature = HashUtils.toMd5(path + "+" + String.join("-", reader.getTitles()));
        DataModel.timestamp = System.currentTimeMillis();

        DataModel.template = "";
        DataModel.numberColumn = findLikelyNumberColumn();
        DataModel.subId = SMSSender.getDefaultSubID();
        loaded = true;
    }

    private static String findLikelyNumberColumn() {
        if (titles == null) return "";

        // Prefer the exact recipient column requested by the WASEEM workflow.
        // This prevents columns such as "رقم الحركة" from ever being selected.
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
        if (data != null) {
            for (String title : titles) {
                String n = normalizeHeader(title);
                if (n.matches(".*(movement|transaction|user|device|result|رقم الحركة|الحركة|رقم الحركة|المستخدم|الجهاز|النتيجة).*")) {
                    continue;
                }
                int count = 0;
                for (HashMap<String, String> row : data) {
                    if (PhoneNumberUtil.isPlausible(row.get(title))) count++;
                }
                if (count > bestCount) {
                    bestCount = count;
                    bestTitle = title;
                }
            }
        }
        return bestTitle;
    }

    private static String normalizeHeader(String title) {
        if (title == null) return "";
        return title.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]+", " ");
    }

    public static void saveAsHistory(Context context) {
        assert loaded();
        HistoryManager.addHistory(context, path, template, subId, numberColumn, signature);
    }

    public static List<HashMap<String, String>> getData() { return data; }
    public static int getRowCount() { return data == null ? 0 : data.size(); }
    public static HashMap<String, String> getRow(int index) { return data.get(index); }
    public static List<Integer> getSourceRowNumbers() { return sourceRowNumbers; }
    public static String[] getTitles() { return titles; }
    public static String getPath() { return path; }
    public static long getTimestamp() { return timestamp; }
    public static String getTemplate() { return template; }
    public static void setTemplate(String template) { DataModel.template = template; }
    public static String getNumberColumn() { return numberColumn; }
    public static void setNumberColumn(String numberColumn) { DataModel.numberColumn = numberColumn; }
    public static String getSignature() { return signature; }
    public static int getSubId() { return subId; }
    public static void setSubId(int subId) { DataModel.subId = subId; }

    public static String getNormalizedPhone(HashMap<String, String> row) {
        return PhoneNumberUtil.normalizeMapValue(row, numberColumn);
    }

    public static int getInvalidPhoneCount() {
        if (!loaded || data == null || numberColumn == null || numberColumn.isEmpty()) return 0;
        int invalid = 0;
        for (HashMap<String, String> row : data) {
            if (!PhoneNumberUtil.isPlausible(row.get(numberColumn))) invalid++;
        }
        return invalid;
    }

    public static int deduplicate() {
        if (!loaded || numberColumn == null || numberColumn.isEmpty()) return 0;

        int originalCount = data.size();
        Set<String> seen = new HashSet<>();
        Iterator<HashMap<String, String>> iterator = data.iterator();
        Iterator<Integer> rowNumberIterator = sourceRowNumbers.iterator();

        while (iterator.hasNext()) {
            HashMap<String, String> row = iterator.next();
            rowNumberIterator.next();
            String number = getNormalizedPhone(row);
            String message = TextParser.parse(template == null ? "" : template, row);
            String key = number + "\u0000" + message;

            // Deduplicate only an identical recipient + identical final message.
            // The same recipient may legitimately appear more than once with different text.
            if (number.isEmpty() || seen.contains(key)) {
                iterator.remove();
                rowNumberIterator.remove();
            } else {
                seen.add(key);
            }
        }
        return originalCount - data.size();
    }

    public static void clear() {
        data = null;
        sourceRowNumbers = null;
        titles = null;
        loaded = false;
        numberColumn = "";
        template = "";
    }
}
