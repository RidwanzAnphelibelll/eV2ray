package com.rscoders.v2ray;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonParser;
import com.rscoders.v2ray.databinding.ActivityEditorBinding;

public class EditorActivity extends AppCompatActivity {

    private ActivityEditorBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditorBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        b.etJson.setText(ConfigManager.loadJson(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Save")
            .setIcon(android.R.drawable.ic_menu_save)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, 2, 0, "Copy")
            .setIcon(android.R.drawable.ic_menu_share)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, 3, 0, "Exit");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                String json = b.etJson.getText().toString().trim();
                if (json.isEmpty()) {
                    Toast.makeText(this, "JSON kosong!", Toast.LENGTH_SHORT).show();
                    return true;
                }
                try {
                    JsonParser.parseString(json);
                    ConfigManager.saveJson(this, json);
                    Toast.makeText(this, "Tersimpan!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "JSON tidak valid: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
                return true;
            case 2:
                android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "json", b.etJson.getText()));
                Toast.makeText(this, "Disalin!", Toast.LENGTH_SHORT).show();
                return true;
            case 3:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
