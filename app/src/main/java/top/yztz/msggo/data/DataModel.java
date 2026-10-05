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
        for (String title : titles) {
            String normalized = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
            if (normalized.matches(".*(phone|mobile|telephone|tel|number|recipient|receiver|sms|contact|هاتف|جوال|موبايل|رقم|المستلم|المستقبل|الهاتف|الجوال|الموبايل|رقم الهاتف|رقم الجوال).*")) {
                return title;
            }
        }

        int bestCount = -1;
        String bestTitle = "";
        if (data != null) {
            for (String title : titles) {
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
        return bestCount > 0 ? bestTitle : "";
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

            if (number.isEmpty() || seen.contains(number)) {
                iterator.remove();
                rowNumberIterator.remove();
            } else {
                seen.add(number);
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
