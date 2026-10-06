package top.yztz.msggo.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import top.yztz.msggo.R;
import top.yztz.msggo.data.DataModel;
import top.yztz.msggo.util.PhoneNumberUtil;
import top.yztz.msggo.util.SentMessageStore;
import top.yztz.msggo.util.TextParser;
import top.yztz.msggo.util.ToastUtil;

public class WhatsAppQueueActivity extends AppCompatActivity {
    private ArrayList<Integer> indices = new ArrayList<>();
    private int position = 0;
    private TextView tvProgress, tvRecipient, tvMessage;
    private Button btnOpen, btnNext, btnSkip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whatsapp_queue);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvProgress = findViewById(R.id.tv_queue_progress);
        tvRecipient = findViewById(R.id.tv_queue_recipient);
        tvMessage = findViewById(R.id.tv_queue_message);
        btnOpen = findViewById(R.id.btn_whatsapp_open);
        btnNext = findViewById(R.id.btn_whatsapp_next);
        btnSkip = findViewById(R.id.btn_whatsapp_skip);

        ArrayList<Integer> incoming = getIntent().getIntegerArrayListExtra("indices");
        if (incoming != null) indices.addAll(incoming);

        if (indices.isEmpty()) {
            ToastUtil.show(this, getString(R.string.no_recipients_selected));
            finish();
            return;
        }

        btnOpen.setOnClickListener(v -> openCurrent());
        btnNext.setOnClickListener(v -> markCurrentSentAndNext());
        btnSkip.setOnClickListener(v -> next());
        showCurrent();
    }

    private void showCurrent() {
        if (position >= indices.size()) {
            tvProgress.setText(getString(R.string.whatsapp_queue_done));
            tvRecipient.setText("");
            tvMessage.setText(getString(R.string.whatsapp_queue_finished));
            btnOpen.setEnabled(false);
            btnNext.setEnabled(false);
            btnSkip.setEnabled(false);
            return;
        }

        Map<String, String> row = DataModel.getRow(indices.get(position));
        String phone = PhoneNumberUtil.fromSpreadsheet(row.get(DataModel.getNumberColumn()));
        String message = DataModel.getMessageForRow((java.util.HashMap<String, String>) row);

        tvProgress.setText(getString(R.string.whatsapp_queue_progress, position + 1, indices.size()));
        tvRecipient.setText(phone);
        tvMessage.setText(message);
        btnOpen.setEnabled(!phone.isEmpty());
        btnNext.setEnabled(true);
        btnSkip.setEnabled(true);
    }

    private void openCurrent() {
        if (position >= indices.size()) return;

        Map<String, String> row = DataModel.getRow(indices.get(position));
        String phone = PhoneNumberUtil.fromSpreadsheet(row.get(DataModel.getNumberColumn()));
        String waNumber = toWhatsAppNumber(phone);
        String message = DataModel.getMessageForRow((java.util.HashMap<String, String>) row);

        if (waNumber.isEmpty()) {
            ToastUtil.show(this, getString(R.string.invalid_numbers_title));
            return;
        }

        // Prefer the WhatsApp deep link so Android opens the installed app
        // directly. Support both WhatsApp Messenger and WhatsApp Business.
        Uri deepLink = Uri.parse("whatsapp://send?phone=" + waNumber + "&text=" + Uri.encode(message));
        Intent intent = new Intent(Intent.ACTION_VIEW, deepLink);

        try {
            intent.setPackage("com.whatsapp");
            startActivity(intent);
            return;
        } catch (ActivityNotFoundException ignored) {
        }

        try {
            intent.setPackage("com.whatsapp.w4b");
            startActivity(intent);
            return;
        } catch (ActivityNotFoundException ignored) {
        }

        // Some Android/WhatsApp builds expose only the web URL handler.
        Uri webUri = Uri.parse("https://wa.me/" + waNumber + "?text=" + Uri.encode(message));
        Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
        try {
            startActivity(webIntent);
        } catch (ActivityNotFoundException e) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.whatsapp_not_installed_title)
                    .setMessage(R.string.whatsapp_not_installed_msg)
                    .setPositiveButton(R.string.ok, null)
                    .show();
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

    private void markCurrentSentAndNext() {
        if (position >= indices.size()) return;
        Map<String, String> row = DataModel.getRow(indices.get(position));
        String phone = PhoneNumberUtil.fromSpreadsheet(row.get(DataModel.getNumberColumn()));
        String message = DataModel.getMessageForRow((java.util.HashMap<String, String>) row);
        String scope = SentMessageStore.scopeKey(this, DataModel.getPath());
        SentMessageStore.markSent(this, scope, phone, message);
        next();
    }

    private void next() {
        if (position < indices.size()) position++;
        showCurrent();
    }

    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.whatsapp_queue_exit_title)
                .setMessage(R.string.whatsapp_queue_exit_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> finish())
                .show();
    }
}
