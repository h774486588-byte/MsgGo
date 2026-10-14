package top.yztz.msggo.activities;

import android.os.Bundle;
import android.app.DatePickerDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Locale;

import top.yztz.msggo.R;
import top.yztz.msggo.util.ArchiveStore;
import top.yztz.msggo.util.ImportedNotificationStore;

/** Searchable, persistent archive of SMS notifications confirmed as sent. */
public class ArchiveActivity extends AppCompatActivity {
    private ArchiveStore store;
    private ImportedNotificationStore importedStore;
    private TextInputEditText phoneInput, fromInput, toInput, textInput;
    private TextView count;
    private LinearLayout results;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ArchiveStore(this);
        importedStore = new ImportedNotificationStore(this);
        buildUi();
        runSearch();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("أرشيف الإشعارات");
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        body.setPadding(pad, pad, pad, pad);

        phoneInput = addField(body, "رقم الهاتف (اختياري)", "مثال: 777123456");
        fromInput = addField(body, "من تاريخ (اختياري)", "اضغط لاختيار تاريخ البداية");
        toInput = addField(body, "إلى تاريخ (اختياري)", "اضغط لاختيار تاريخ النهاية");
        fromInput.setFocusable(false);
        fromInput.setClickable(true);
        toInput.setFocusable(false);
        toInput.setClickable(true);
        fromInput.setOnClickListener(v -> chooseDate(fromInput));
        toInput.setOnClickListener(v -> chooseDate(toInput));
        textInput = addField(body, "كلمة من نص الرسالة (اختياري)", "ابحث في محتوى الإشعار");

        TextView hint = new TextView(this);
        hint.setText("اختر تاريخًا واحدًا في الحقلين لعرض ذلك اليوم فقط، أو اختر تاريخ البداية والنهاية لعرض فترة كاملة.");
        body.addView(hint);

        Button todayButton = new Button(this);
        todayButton.setText("عرض إشعارات يوم محدد: اليوم");
        todayButton.setOnClickListener(v -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Calendar.getInstance().getTime());
            fromInput.setText(today);
            toInput.setText(today);
            runSearch();
        });
        body.addView(todayButton, new LinearLayout.LayoutParams(-1, -2));

        Button searchButton = new Button(this);
        searchButton.setText("بحث في الأرشيف");
        searchButton.setOnClickListener(v -> runSearch());
        body.addView(searchButton, new LinearLayout.LayoutParams(-1, -2));

        Button clearButton = new Button(this);
        clearButton.setText("مسح الفلاتر وعرض الكل");
        clearButton.setOnClickListener(v -> {
            phoneInput.setText("");
            fromInput.setText("");
            toInput.setText("");
            textInput.setText("");
            runSearch();
        });
        body.addView(clearButton, new LinearLayout.LayoutParams(-1, -2));

        count = new TextView(this);
        count.setPadding(0, dp(12), 0, dp(8));
        body.addView(count);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private TextInputEditText addField(LinearLayout parent, String label, String placeholder) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(label);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setHint(placeholder);
        layout.addView(input);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        parent.addView(layout, lp);
        return input;
    }

    private void runSearch() {
        String phone = value(phoneInput);
        String from = value(fromInput);
        String to = value(toInput);
        String text = value(textInput);
        results.removeAllViews();

        if ((!from.isEmpty() && !validDate(from)) || (!to.isEmpty() && !validDate(to))) {
            count.setText("صيغة التاريخ غير صحيحة. استخدم YYYY-MM-DD.");
            return;
        }
        if (!from.isEmpty() && !to.isEmpty() && from.compareTo(to) > 0) {
            count.setText("تاريخ البداية يجب ألا يكون بعد تاريخ النهاية.");
            return;
        }

        List<ArchiveStore.Record> rows = store.search(phone, from, to, text);
        List<ImportedNotificationStore.Item> importedRows =
                importedStore.search(phone, from, to, text, "");
        count.setText("الإشعارات المرسلة المؤرشفة: " + rows.size()
                + " | جميع الإشعارات المستوردة: " + importedRows.size());
        if (rows.isEmpty() && importedRows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("لا توجد إشعارات مطابقة.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            results.addView(empty);
            return;
        }

        for (ArchiveStore.Record row : rows) {
            MaterialCardView card = new MaterialCardView(this);
            card.setCardElevation(dp(2));
            card.setRadius(dp(12));
            card.setContentPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);

            TextView recipient = new TextView(this);
            recipient.setText("المستلم: " + row.phone);
            recipient.setTextSize(16);
            recipient.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView eventDate = new TextView(this);
            eventDate.setText("تاريخ الإشعار: " + (row.eventDate.isEmpty() ? "غير محدد" : row.eventDate));

            TextView content = new TextView(this);
            content.setText("الرسالة: " + row.content);
            content.setPadding(0, dp(6), 0, dp(6));

            TextView sent = new TextView(this);
            sent.setText("تأكيد الإرسال: " + ArchiveStore.formatSentAt(row.sentAt));

            box.addView(recipient);
            box.addView(eventDate);
            box.addView(content);
            box.addView(sent);
            card.addView(box);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            results.addView(card, lp);
        }

        // Show all imported notifications as well as the confirmed-sent archive.
        // Status is explicit so pending/failed historical rows are not mistaken for sent messages.
        for (ImportedNotificationStore.Item row : importedRows) {
            MaterialCardView card = new MaterialCardView(this);
            card.setCardElevation(dp(2));
            card.setRadius(dp(12));
            card.setContentPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);

            TextView recipient = new TextView(this);
            recipient.setText("المستلم: " + row.phone);
            recipient.setTextSize(16);
            TextView eventDate = new TextView(this);
            eventDate.setText("تاريخ الإشعار: " + (row.eventDate == null || row.eventDate.isEmpty() ? "غير محدد" : row.eventDate));
            TextView content = new TextView(this);
            content.setText("الرسالة: " + row.message);
            content.setPadding(0, dp(6), 0, dp(6));
            TextView status = new TextView(this);
            status.setText("الحالة: " + statusLabel(row.status));

            box.addView(recipient);
            box.addView(eventDate);
            box.addView(content);
            box.addView(status);
            card.addView(box);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            results.addView(card, lp);
        }
    }

    private String statusLabel(String status) {
        if (ImportedNotificationStore.SENT.equals(status)) return "تم الإرسال";
        if (ImportedNotificationStore.FAILED.equals(status)) return "فشل الإرسال";
        if (ImportedNotificationStore.ARCHIVED.equals(status)) return "مؤرشف";
        return "معلق / لم يتم تأكيد الإرسال";
    }

    private void chooseDate(TextInputEditText target) {
        Calendar calendar = Calendar.getInstance();
        String current = value(target);
        if (validDate(current)) {
            try {
                java.util.Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(current);
                if (parsed != null) calendar.setTime(parsed);
            } catch (Exception ignored) {}
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            String selected = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
            target.setText(selected);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private boolean validDate(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        java.util.Date parsed = format.parse(value, position);
        return parsed != null && position.getIndex() == value.length();
    }

    private String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}