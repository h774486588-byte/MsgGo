package top.yztz.msggo.data;

import android.content.Context;

import java.io.Serializable;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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

    /**
     * Returns the message for a spreadsheet row. If the user has not entered a
     * global template, use the row's own "الرسالة" column so imported messages
     * are previewed and sent exactly as provided in Excel.
     */
    public static String getMessageForRow(HashMap<String, String> row) {
        if (row == null) return "";
        String currentTemplate = template == null ? "" : template.trim();
        if (!currentTemplate.isEmpty()) {
            return TextParser.parse(currentTemplate, row);
        }
        String message = row.get(findMessageColumn());
        return message == null ? "" : message;
    }

    private static String findMessageColumn() {
        if (titles == null) return "";
        for (String title : titles) {
            String n = normalizeHeader(title);
            if ("الرسالة".equals(n) || "message".equals(n) || "sms".equals(n)
                    || "نص الرسالة".equals(n) || "محتوى الرسالة".equals(n)) {
                return title;
            }
        }
        return "";
    }
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

    /**
     * Prepare the current file for the next sending run:
     * 1) remove duplicate recipient+message rows,
     * 2) remove messages already confirmed as sent for this source file,
     * 3) sort the remaining queue by the "الوقت" column, oldest first.
     */
    public static synchronized int prepareQueue(Context context) {
        if (!loaded) return 0;

        int before = data == null ? 0 : data.size();
        filterToToday();
        deduplicate();

        if (data != null && sourceRowNumbers != null) {
            String scope = top.yztz.msggo.util.SentMessageStore.scopeKey(context, path);
            Iterator<HashMap<String, String>> iterator = data.iterator();
            Iterator<Integer> rowIterator = sourceRowNumbers.iterator();

            while (iterator.hasNext()) {
                HashMap<String, String> row = iterator.next();
                rowIterator.next();
                String phone = getNormalizedPhone(row);
                String message = getMessageForRow(row);
                if (top.yztz.msggo.util.SentMessageStore.isSent(context, scope, phone, message)) {
                    iterator.remove();
                    rowIterator.remove();
                }
            }
        }

        sortByQueueTime();
        return before - (data == null ? 0 : data.size());
    }

    private static void filterToToday() {
        if (data == null || sourceRowNumbers == null) return;
        String timeColumn = findTimeColumn();
        Iterator<HashMap<String, String>> iterator = data.iterator();
        Iterator<Integer> rowIterator = sourceRowNumbers.iterator();
        while (iterator.hasNext()) {
            HashMap<String, String> row = iterator.next();
            rowIterator.next();
            if (!isTodayRow(row, timeColumn)) {
                iterator.remove();
                rowIterator.remove();
            }
        }
    }

    public static boolean isTodayRow(HashMap<String, String> row) {
        return isTodayRow(row, findTimeColumn());
    }

    private static boolean isTodayRow(HashMap<String, String> row, String timeColumn) {
        if (row == null || timeColumn == null || timeColumn.isEmpty()) return false;
        LocalDateTime parsed = parseQueueDateTime(row.get(timeColumn));
        return parsed != null && parsed.toLocalDate().equals(LocalDate.now());
    }

    private static void sortByQueueTime() {
        if (data == null || sourceRowNumbers == null || data.size() < 2) return;

        final String timeColumn = findTimeColumn();
        if (timeColumn.isEmpty()) return;

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) order.add(i);

        Collections.sort(order, (left, right) -> {
            long leftTime = parseQueueTime(data.get(left).get(timeColumn));
            long rightTime = parseQueueTime(data.get(right).get(timeColumn));

            if (leftTime == Long.MAX_VALUE && rightTime == Long.MAX_VALUE) return Integer.compare(left, right);
            if (leftTime == Long.MAX_VALUE) return 1;
            if (rightTime == Long.MAX_VALUE) return -1;
            int result = Long.compare(leftTime, rightTime);
            return result != 0 ? result : Integer.compare(left, right);
        });

        List<HashMap<String, String>> sortedData = new ArrayList<>(data.size());
        List<Integer> sortedRows = new ArrayList<>(sourceRowNumbers.size());
        for (Integer index : order) {
            sortedData.add(data.get(index));
            sortedRows.add(sourceRowNumbers.get(index));
        }
        data = sortedData;
        sourceRowNumbers = sortedRows;
    }

    private static String findTimeColumn() {
        if (titles == null) return "";
        for (String title : titles) {
            String n = normalizeHeader(title);
            if ("الوقت".equals(n) || "time".equals(n)
                    || "datetime".equals(n) || "date time".equals(n)
                    || "التاريخ والوقت".equals(n)) {
                return title;
            }
        }
        return "";
    }

    private static LocalDateTime parseQueueDateTime(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        String[] patterns = {
                "H:mm M/d/yy", "H:mm:ss M/d/yy",
                "M/d/yy H:mm", "M/d/yy H:mm:ss",
                "H:mm M/d/yyyy", "H:mm:ss M/d/yyyy",
                "M/d/yyyy H:mm", "M/d/yyyy H:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                return LocalDateTime.parse(v, DateTimeFormatter.ofPattern(pattern, Locale.US));
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private static long parseQueueTime(String value) {
        LocalDateTime parsed = parseQueueDateTime(value);
        if (parsed == null) return Long.MAX_VALUE;
        return parsed.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
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
            String message = getMessageForRow(row);
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
