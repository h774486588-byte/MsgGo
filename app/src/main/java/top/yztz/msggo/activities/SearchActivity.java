package top.yztz.msggo.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import top.yztz.msggo.R;
import top.yztz.msggo.data.DataModel;
import top.yztz.msggo.data.Message;
import top.yztz.msggo.util.PhoneNumberUtil;
import top.yztz.msggo.util.TextParser;
import top.yztz.msggo.services.SMSSender;

public class SearchActivity extends AppCompatActivity {
    private LinearLayout results;
    private TextView count;
    private TextInputEditText search;
    private int requestCode = 50000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        results = findViewById(R.id.search_results);
        count = findViewById(R.id.tv_search_count);
        search = findViewById(R.id.et_search);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderResults(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void renderResults(String query) {
        results.removeAllViews();
        String q = normalize(query);

        if (q.isEmpty()) {
            count.setText(R.string.search_enter_term);
            return;
        }

        int found = 0;
        String[] titles = DataModel.getTitles();
        for (int i = 0; i < DataModel.getRowCount(); i++) {
            HashMap<String, String> row = DataModel.getRow(i);
            StringBuilder haystack = new StringBuilder();

            String recipient = PhoneNumberUtil.formatForDisplay(row.get(DataModel.getNumberColumn()));
            haystack.append(normalize(recipient)).append(' ');

            if (titles != null) {
                for (String title : titles) {
                    String value = row.get(title);
                    if (value != null) haystack.append(normalize(value)).append(' ');
                }
            }

            if (!haystack.toString().contains(q)) continue;

            found++;
            addResultCard(i, row, recipient);
        }

        count.setText(getString(R.string.search_results_count, found));
        if (found == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.search_no_results);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(24, 48, 24, 48);
            results.addView(empty);
        }
    }

    private void addResultCard(int index, Map<String, String> row, String recipient) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(16, 6, 16, 6);
        card.setLayoutParams(cardParams);
        card.setContentPadding(16, 14, 16, 14);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView phone = new TextView(this);
        phone.setText(getString(R.string.search_recipient_format, recipient));
        phone.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);

        TextView message = new TextView(this);
        String content = TextParser.parse(DataModel.getTemplate(), row);
        message.setText(content);
        message.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        message.setPadding(0, 8, 0, 8);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button sms = new Button(this);
        sms.setText(R.string.send_sms_individual);
        sms.setOnClickListener(v -> sendSms(row));

        Button whatsapp = new Button(this);
        whatsapp.setText(R.string.send_whatsapp_individual);
        whatsapp.setOnClickListener(v -> openWhatsApp(recipient, content));

        buttons.addView(sms, new LinearLayout.LayoutParams(0, 52, 1));
        LinearLayout.LayoutParams waParams = new LinearLayout.LayoutParams(0, 52, 1);
        waParams.setMargins(8, 0, 0, 0);
        buttons.addView(whatsapp, waParams);

        box.addView(phone);
        box.addView(message);
        box.addView(buttons);
        card.addView(box);
        results.addView(card);
    }

    private void sendSms(Map<String, String> row) {
        String phone = PhoneNumberUtil.fromSpreadsheet(row.get(DataModel.getNumberColumn()));
        String content = TextParser.parse(DataModel.getTemplate(), row);

        if (!PhoneNumberUtil.isPlausible(phone)) {
            Toast.makeText(this, R.string.invalid_numbers_title, Toast.LENGTH_SHORT).show();
            return;
        }

        int code = requestCode++;
        boolean submitted;
        try {
            submitted = SMSSender.sendMessage(
                    this, content, phone, DataModel.getSubId(), code,
                    "individual-" + code);
        } catch (SecurityException e) {
            submitted = false;
        }

        Toast.makeText(this,
                submitted ? R.string.individual_sms_submitted : R.string.individual_sms_failed,
                Toast.LENGTH_SHORT).show();
    }

    private void openWhatsApp(String phone, String message) {
        String waNumber = toWhatsAppNumber(phone);
        if (waNumber.isEmpty()) {
            Toast.makeText(this, R.string.invalid_numbers_title, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = Uri.parse("https://wa.me/" + waNumber + "?text=" + Uri.encode(message));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            intent.setPackage("com.whatsapp");
            startActivity(intent);
        } catch (ActivityNotFoundException first) {
            try {
                intent.setPackage("com.whatsapp.w4b");
                startActivity(intent);
            } catch (ActivityNotFoundException second) {
                intent.setPackage(null);
                startActivity(intent);
            }
        }
    }

    private String toWhatsAppNumber(String phone) {
        String value = PhoneNumberUtil.normalizeForSms(phone);
        if (value.startsWith("+")) value = value.substring(1);
        if (value.startsWith("00967")) return value.substring(2);
        if (value.matches("7\\d{8}")) return "967" + value;
        if (value.matches("9677\\d{8}")) return value;
        return value.matches("\\d{7,15}") ? value : "";
    }

    private String normalize(String value) {
        return PhoneNumberUtil.normalizeDigits(value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}