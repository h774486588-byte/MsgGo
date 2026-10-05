package top.yztz.msggo.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class PhoneNumberUtil {
    private static final Pattern SCIENTIFIC = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)[eE][+-]?\\d+$");
    private static final Pattern DECIMAL_INTEGER = Pattern.compile("^[+-]?\\d+\\.0+$");

    private PhoneNumberUtil() {}

    /** Converts Arabic/Persian digits to ASCII digits without changing their numeric value. */
    public static String normalizeDigits(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') {
                out.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') {
                out.append((char) ('0' + (c - '\u06F0')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Repairs common spreadsheet representations such as 733222087.0 or 7.33222087E8.
     * It does not invent a country code and does not add/remove leading digits intentionally.
     */
    public static String fromSpreadsheet(String raw) {
        String value = normalizeDigits(raw)
                .replace('\u00A0', ' ')
                .replace("\u200E", "")
                .replace("\u200F", "")
                .replace("\u202A", "")
                .replace("\u202B", "")
                .replace("\u202C", "")
                .trim();

        if (value.isEmpty()) return "";

        try {
            if (SCIENTIFIC.matcher(value).matches()) {
                value = new BigDecimal(value).toPlainString();
            }
        } catch (NumberFormatException ignored) {
            // Keep original value; validation will reject it if it is not a phone number.
        }

        if (DECIMAL_INTEGER.matcher(value).matches()) {
            int dot = value.indexOf('.');
            value = value.substring(0, dot);
        }

        return normalizeForSms(value);
    }

    /**
     * Removes presentation separators while preserving a leading + and all digits.
     */
    public static String normalizeForSms(String raw) {
        String value = normalizeDigits(raw).trim();
        if (value.isEmpty()) return "";

        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
            } else if (c == '+' && out.length() == 0) {
                out.append(c);
            } else if (c == ' ' || c == '-' || c == '(' || c == ')' || c == '.' || c == '\u00A0') {
                // Common display separators.
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static boolean isPlausible(String raw) {
        String value = fromSpreadsheet(raw);
        if (value.isEmpty()) return false;

        int digitCount = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') digitCount++;
            else if (i != 0 || c != '+') return false;
        }

        // Accept local numbers such as Yemen's 9-digit mobile numbers and international
        // numbers; Android's SmsManager requires a non-empty destination address.
        return digitCount >= 3 && digitCount <= 15;
    }

    public static String normalizeMapValue(Map<String, String> row, String column) {
        if (row == null || column == null) return "";
        return fromSpreadsheet(row.get(column));
    }

    public static String formatForDisplay(String raw) {
        String normalized = fromSpreadsheet(raw);
        return normalized.isEmpty() ? "—" : normalized;
    }
}
