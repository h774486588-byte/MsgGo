package top.yztz.msggo.activities;

import android.os.Bundle;
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

import top.yztz.msggo.R;
import top.yztz.msggo.util.ArchiveStore;

/** Searchable, persistent archive of SMS notifications confirmed as sent. */
public class ArchiveActivity extends AppCompatActivity {
    private ArchiveStore store;
    private TextInputEditText phoneInput, fromInput, toInput, textInput;
    private TextView count;
    private LinearLayout results;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ArchiveStore(this);
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
        fromInput = addField(body, "من تاريخ (اختياري)", "YYYY-MM-DD");
        toInput = addField(body, "إلى تاريخ (اختياري)", "YYYY-MM-DD");
        textInput = addField(body, "كلمة من نص الرسالة (اختياري)", "ابحث في محتوى الإشعار");

        TextView hint = new TextView(this);
        hint.setText("استخدم YYYY-MM-DD للتاريخ. اترك الحقول فارغة لعرض الأرشيف كاملًا.");
        body.addView(hint);

        Button searchButton = new Button(this);
        searchButton.setText("بحث في الأرشيف");
        searchButton.setOnClickListener(v -> runSearch());
        body.addView(searchButton, new LinearLayout.LayoutParams(-1, -2));

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
        count.setText("عدد الإشعارات المؤرشفة المطابقة: " + rows.size());
        if (rows.isEmpty()) {
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
    }

    private boolean validDate(String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}