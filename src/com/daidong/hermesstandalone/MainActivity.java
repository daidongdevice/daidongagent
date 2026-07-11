package com.daidong.hermesstandalone;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.*;
import android.text.method.ScrollingMovementMethod;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.Handler;

public class MainActivity extends Activity {
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private boolean yolo = true;
    private boolean loading = false;
    private LinearLayout loadingIndicator;
    private boolean shizukuAvailable = false;
    private boolean shizukuGranted = false;
    private static final int SHIZUKU_REQUEST_CODE = 1001;
    private Messenger serviceMessenger;
    private IBinder shizukuServiceBinder;
    private boolean serviceBound = false;
    private String pendingExecResult;
    private final Object execLock = new Object();

    private Messenger replyMessenger = new Messenger(new Handler(new Handler.Callback() {
        public boolean handleMessage(Message msg) {
            if (msg.what == ShizukuUserService.MSG_RESULT) {
                synchronized (execLock) {
                    Bundle data = msg.getData();
                    data.setClassLoader(MainActivity.class.getClassLoader());
                    pendingExecResult = data.getString("result");
                    execLock.notify();
                }
            }
            return true;
        }
    }));

    private ServiceConnection shizukuServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            shizukuServiceBinder = service;
            serviceMessenger = new Messenger(service);
            serviceBound = true;
            addBot("\u2705 Shizuku Service da san sang. Ban co the yeu cau mo app!", "He thong");
        }
        public void onServiceDisconnected(ComponentName name) {
            shizukuServiceBinder = null;
            serviceMessenger = null;
            serviceBound = false;
        }
    };

    // --- Color palette: refined dark theme ---
    private final int bg           = Color.rgb(8, 12, 20);
    private final int surface      = Color.rgb(15, 22, 36);
    private final int surfaceLight = Color.rgb(22, 31, 50);
    private final int userBubble   = Color.rgb(0, 122, 255);
    private final int botBubbleBg  = Color.rgb(25, 35, 50);
    private final int accent       = Color.rgb(0, 122, 255);
    private final int textPrimary  = Color.rgb(242, 245, 250);
    private final int textSecondary= Color.rgb(139, 150, 170);
    private final int textMuted    = Color.rgb(99, 110, 130);
    private final int border       = Color.rgb(30, 40, 55);
    private final int yoloColor    = Color.rgb(0, 122, 255);
    private final int yoloOffColor = Color.rgb(58, 58, 70);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        buildUi();
        RuntimeInfo info = ensureRuntime();
        addBot("\u2728 DaidongAgent da san sang.\n\n" + info.message +
               "\n\n\ud83d\udca1 Ban co the chat voi Hermes AI qua 9Router. Nhan Settings de doi model, New de bat dau chat moi.",
               "Da san sang");
        updateRuntimeStatus();
        initShizuku();
    }

    // ═══════════════════════════════════════════
    //  UI CONSTRUCTION
    // ═══════════════════════════════════════════

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);
        setContentView(root);

        // --- Header Card ---
        LinearLayout headerCard = new LinearLayout(this);
        headerCard.setOrientation(LinearLayout.VERTICAL);
        headerCard.setPadding(dp(16), dp(14), dp(16), dp(12));
        headerCard.setBackground(roundedRect(surface, dp(0), dp(0), dp(16), dp(16)));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("D");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(18);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        int logoSize = dp(38);
        logo.setWidth(logoSize);
        logo.setHeight(logoSize);
        logo.setBackground(circle(accent));
        titleRow.addView(logo);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("DaidongAgent");
        title.setTextColor(textPrimary);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("AI Agent \u00b7 9Router + DeepSeek");
        subtitle.setTextColor(textMuted);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, dp(2), 0, 0);
        titleCol.addView(subtitle);
        titleRow.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        headerCard.addView(titleRow);

        status = new TextView(this);
        status.setTextColor(textMuted);
        status.setTextSize(11);
        status.setPadding(0, dp(10), 0, 0);
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        headerCard.addView(status);

        root.addView(headerCard);

        // --- Divider ---
        View divider = new View(this);
        divider.setBackgroundColor(border);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(divider);

        // --- Control buttons row ---
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(10));
        controls.setBackgroundColor(surface);
        root.addView(controls);

        // YOLO button
        Button yoloBtn = pillButton("\u26a1 YOLO");
        yoloBtn.setBackground(pillBg(yoloColor, 20));
        yoloBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                yolo = !yolo;
                Button b = (Button) v;
                if (yolo) {
                    b.setText("\u26a1 YOLO");
                    b.setBackground(pillBg(yoloColor, 20));
                } else {
                    b.setText("\ud83d\udee1 Manual");
                    b.setBackground(pillBg(yoloOffColor, 20));
                }
                addBot("\u2705 Da chuyen sang che do " + (yolo ? "YOLO" : "Manual"),
                       "Cai dat");
            }
        });
        controls.addView(yoloBtn, pillLp());

        // Status button
        Button statusBtn = pillButton("\ud83d\udcca Status");
        statusBtn.setBackground(pillBg(surfaceLight, 20));
        statusBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { updateRuntimeStatusWithMessage(); }
        });
        controls.addView(statusBtn, pillLp());

        // Settings button
        Button settingsBtn = pillButton("\u2699 Settings");
        settingsBtn.setBackground(pillBg(surfaceLight, 20));
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSettingsDialog(); }
        });
        controls.addView(settingsBtn, pillLp());

        // New chat button
        Button newBtn = pillButton("\u2728 New");
        newBtn.setBackground(pillBg(surfaceLight, 20));
        newBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                messages.removeAllViews();
                String result = runBridgeCommand("--reset-history");
                addBot("\ud83c\udf31 New chat bat dau.\n" + result, "He thong");
            }
        });
        controls.addView(newBtn, pillLp());

        // --- Chat area ---
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(4), dp(8), dp(4), dp(8));
        scroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Loading indicator
        loadingIndicator = new LinearLayout(this);
        loadingIndicator.setOrientation(LinearLayout.HORIZONTAL);
        loadingIndicator.setGravity(Gravity.CENTER);
        loadingIndicator.setPadding(0, dp(8), 0, dp(4));
        loadingIndicator.setVisibility(View.GONE);
        for (int i = 0; i < 3; i++) {
            View dot = new View(this);
            dot.setBackground(circle(accent));
            int s = dp(6);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(s, s);
            dlp.setMargins(dp(3), 0, dp(3), 0);
            dot.setLayoutParams(dlp);
            loadingIndicator.addView(dot);
        }
        messages.addView(loadingIndicator);

        // --- Input area ---
        LinearLayout inputArea = new LinearLayout(this);
        inputArea.setOrientation(LinearLayout.HORIZONTAL);
        inputArea.setGravity(Gravity.CENTER_VERTICAL);
        inputArea.setPadding(dp(12), dp(8), dp(12), dp(12));
        inputArea.setBackground(roundedRect(surface, dp(16), dp(16), 0, 0));

        LinearLayout inputContainer = new LinearLayout(this);
        inputContainer.setOrientation(LinearLayout.HORIZONTAL);
        inputContainer.setGravity(Gravity.CENTER_VERTICAL);
        inputContainer.setBackground(roundedRect(surfaceLight, 24));
        inputContainer.setPadding(dp(4), dp(2), dp(2), dp(2));

        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setHint("Nhan voi Hermes...");
        input.setHintTextColor(textMuted);
        input.setTextColor(textPrimary);
        input.setTextSize(15);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(dp(12), dp(10), dp(4), dp(10));
        input.setGravity(Gravity.CENTER_VERTICAL);
        inputContainer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        Button sendBtn = new Button(this);
        sendBtn.setText("\u27a4");
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setTextSize(16);
        sendBtn.setAllCaps(false);
        sendBtn.setTypeface(Typeface.DEFAULT_BOLD);
        int sendW = dp(48);
        int sendH = dp(44);
        sendBtn.setWidth(sendW);
        sendBtn.setHeight(sendH);
        sendBtn.setBackground(roundedRect(accent, dp(10)));
        sendBtn.setPadding(0, 0, 0, 0);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sendMessage(); }
        });
        inputContainer.addView(sendBtn);

        inputArea.addView(inputContainer, new LinearLayout.LayoutParams(-1, -2));
        root.addView(inputArea);
    }

    // ═══════════════════════════════════════════
    //  DRAWABLE HELPERS
    // ═══════════════════════════════════════════

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private GradientDrawable roundedRect(int color, float tl, float tr, float br, float bl) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return d;
    }

    private GradientDrawable roundedRect(int color, float radius) {
        return roundedRect(color, radius, radius, radius, radius);
    }

    private GradientDrawable pillBg(int color, float radius) {
        return roundedRect(color, radius);
    }

    private LinearLayout.LayoutParams pillLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
        lp.setMargins(0, 0, dp(6), 0);
        return lp;
    }

    private Button pillButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(dp(4), dp(2), dp(4), dp(2));
        return b;
    }

    private GradientDrawable bubbleBg(int color, boolean isUser) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        float r = dp(16);
        if (isUser) {
            d.setCornerRadii(new float[]{r, r, dp(4), dp(4), r, r, r, r});
        } else {
            d.setCornerRadii(new float[]{dp(4), dp(4), r, r, r, r, r, r});
        }
        return d;
    }

    // ═══════════════════════════════════════════
    //  SETTINGS DIALOG
    // ═══════════════════════════════════════════

    private void showSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);

        final EditText baseUrl = styledInput("http://127.0.0.1:20128/v1", "Base URL");
        final EditText model = styledInput("ds/deepseek-v4-flash", "Model ID");
        final EditText toolsets = styledInput("safe", "Toolsets");

        box.addView(settingsLabel("\ud83c\udf10 Base URL"));
        box.addView(baseUrl);
        box.addView(settingsLabel("\ud83e\udd16 Model"));
        box.addView(model);
        box.addView(settingsLabel("\ud83d\udd27 Toolsets"));
        box.addView(toolsets);

        new AlertDialog.Builder(this)
            .setTitle("\u2699 DaidongAgent Settings")
            .setView(box)
            .setPositiveButton("\ud83d\udcbe Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    saveBridgeSettings(
                        baseUrl.getText().toString().trim(),
                        model.getText().toString().trim(),
                        toolsets.getText().toString().trim());
                }
            })
            .setNegativeButton("Huy", null)
            .show();
    }

    private EditText styledInput(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(textPrimary);
        e.setHintTextColor(textMuted);
        e.setTextSize(14);
        e.setBackground(roundedRect(surfaceLight, dp(10)));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    private TextView settingsLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textSecondary);
        tv.setTextSize(13);
        tv.setPadding(0, dp(12), 0, dp(5));
        return tv;
    }

    // ═══════════════════════════════════════════
    //  MESSAGE BUBBLES
    // ═══════════════════════════════════════════

    private void addUser(String text) {
        addMessageBubble(text, true, "Ban");
    }

    private void addBot(String text, String sender) {
        addMessageBubble(text, false, sender);
    }

    private void addMessageBubble(String text, boolean isUser, String sender) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(isUser ? dp(48) : dp(4), dp(4), isUser ? dp(4) : dp(48), dp(4));
        container.setGravity(isUser ? Gravity.RIGHT : Gravity.LEFT);

        if (!isUser) {
            TextView senderLabel = new TextView(this);
            senderLabel.setText(sender);
            senderLabel.setTextColor(textMuted);
            senderLabel.setTextSize(11);
            senderLabel.setPadding(dp(6), 0, 0, dp(3));
            container.addView(senderLabel);
        }

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(textPrimary);
        bubble.setTextSize(15);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setMovementMethod(new ScrollingMovementMethod());
        bubble.setBackground(bubbleBg(isUser ? userBubble : botBubbleBg, isUser));
        container.addView(bubble);

        // Meta row: time + copy
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(isUser ? Gravity.RIGHT : Gravity.LEFT);
        meta.setPadding(dp(6), dp(3), dp(2), 0);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setTextColor(textMuted);
        timeView.setTextSize(10);
        meta.addView(timeView);

        if (!isUser) {
            Button copyBtn = new Button(this);
            copyBtn.setText("\ud83d\udccb");
            copyBtn.setTextSize(10);
            copyBtn.setAllCaps(false);
            copyBtn.setBackgroundColor(Color.TRANSPARENT);
            copyBtn.setTextColor(textMuted);
            copyBtn.setPadding(dp(6), 0, dp(2), 0);
            copyBtn.setMinWidth(0);
            copyBtn.setMinHeight(0);
            final String copyText = text;
            copyBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("DaidongAgent", copyText));
                    Toast.makeText(MainActivity.this, "\u2705 Da copy", Toast.LENGTH_SHORT).show();
                }
            });
            meta.addView(copyBtn);
        }

        container.addView(meta);
        messages.addView(container, messages.getChildCount() - 1);
        scroll.post(new Runnable() {
            public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    // ═══════════════════════════════════════════
    //  MESSAGE LOGIC
    // ═══════════════════════════════════════════

    private void sendMessage() {
        final String text = input.getText().toString().trim();
        if (text.length() == 0) return;
        input.setText("");
        hideKeyboard();
        addUser(text);

        setLoading(true);

        RuntimeInfo info = ensureRuntime();
        File py = new File(getRuntimeDir(), "bin/python");
        File bridge = new File(getRuntimeDir(), "hermes_bridge.py");
        if (!py.exists() || !bridge.exists()) {
            setLoading(false);
            addBot("\u274c Khong the goi Hermes bridge.\n\n" + info.message, "Loi");
            return;
        }

        final boolean yoloNow = yolo;
        new Thread(new Runnable() {
            public void run() {
                final String reply = runHermesBridge(text, yoloNow);
                // Process [EXEC:...] blocks on background thread (Shizuku calls block)
                final String execBlock = extractExecBlock(reply);
                String processedReply = reply;
                if (execBlock != null && shizukuAvailable && shizukuGranted && serviceBound) {
                    final String execResult = execShizuku(execBlock);
                    processedReply = processExecBlocks(reply, execBlock, execResult);
                }
                final String finalReply = processedReply;
                runOnUiThread(new Runnable() {
                    public void run() {
                        setLoading(false);
                        addBot(finalReply, "Hermes AI");
                        updateRuntimeStatus();
                    }
                });
            }
        }).start();
    }

    private void setLoading(boolean show) {
        loading = show;
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            scroll.post(new Runnable() {
                public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
            });
        }
    }

    // ═══════════════════════════════════════════
    //  BRIDGE INTERFACE
    // ═══════════════════════════════════════════

    private String runHermesBridge(String text, boolean yoloNow) {
        try {
            File py = new File(getRuntimeDir(), "bin/python");
            File bridge = new File(getRuntimeDir(), "hermes_bridge.py");
            List<String> cmd = new ArrayList<String>();
            cmd.add(py.getAbsolutePath());
            cmd.add(bridge.getAbsolutePath());
            if (yoloNow) cmd.add("--yolo");
            cmd.add(text);
            ProcessBuilder pb = basePythonProcess(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            long deadline = System.currentTimeMillis() + 180000;
            while (true) {
                try {
                    int exit = p.exitValue();
                    String out = readMergedProcess(p).trim();
                    if (out.length() == 0) out = "\u26a0 Hermes bridge khong tra output.";
                    if (exit != 0) out = out + "\n\n(exit=" + exit + ")";
                    return out;
                } catch (IllegalThreadStateException stillRunning) {
                    if (System.currentTimeMillis() > deadline) {
                        p.destroy();
                        return "\u23f1 Hermes bridge timeout sau 180 giay.";
                    }
                    Thread.sleep(300);
                }
            }
        } catch (Exception e) {
            return "\u274c Bridge loi: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private String runBridgeCommand(String arg) {
        try {
            RuntimeInfo info = ensureRuntime();
            File py = new File(getRuntimeDir(), "bin/python");
            File bridge = new File(getRuntimeDir(), "hermes_bridge.py");
            if (!py.exists() || !bridge.exists()) return "Bridge missing. " + info.message;
            List<String> cmd = new ArrayList<String>();
            cmd.add(py.getAbsolutePath());
            cmd.add(bridge.getAbsolutePath());
            cmd.add(arg);
            ProcessBuilder pb = basePythonProcess(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return readProcess(p);
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ═══════════════════════════════════════════
    //  SETTINGS PERSISTENCE
    // ═══════════════════════════════════════════

    private File getHermesHomeDir() {
        return new File(getFilesDir(), "hermes-home");
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void saveBridgeSettings(String baseUrl, String model, String toolsets) {
        if (baseUrl.length() == 0) baseUrl = "http://127.0.0.1:20128/v1";
        if (model.length() == 0) model = "ds/deepseek-v4-flash";
        if (toolsets.length() == 0) toolsets = "safe";
        try {
            File home = getHermesHomeDir();
            if (!home.exists()) home.mkdirs();
            File settings = new File(home, "bridge_settings.json");
            String json = "{\n" +
                "  \"provider\": \"custom:9router\",\n" +
                "  \"model\": \"" + jsonEscape(model) + "\",\n" +
                "  \"base_url\": \"" + jsonEscape(baseUrl) + "\",\n" +
                "  \"toolsets\": \"" + jsonEscape(toolsets) + "\",\n" +
                "  \"history_turns\": 8\n" +
                "}\n";
            FileWriter fw = new FileWriter(settings, false);
            fw.write(json);
            fw.close();
            addBot("\u2705 Da luu settings:\n\u2022 Base URL: " + baseUrl + "\n\u2022 Model: " + model + "\n\u2022 Toolsets: " + toolsets, "Cai dat");
            updateRuntimeStatus();
        } catch (Exception e) {
            addBot("\u274c Luu settings loi: " + e.getMessage(), "Loi");
        }
    }

    // ═══════════════════════════════════════════
    //  RUNTIME & PROCESS HELPERS
    // ═══════════════════════════════════════════

    private void updateRuntimeStatus() {
        File rt = getRuntimeDir();
        File py = new File(rt, "bin/python");
        File hermes = new File(rt, "hermes-agent");
        File manifest = new File(rt, "MANIFEST.txt");
        File bridge = new File(rt, "hermes_bridge.py");
        File history = new File(getHermesHomeDir(), "chat_history.json");
        status.setText((manifest.exists() ? "\u25cf" : "\u25cb") + " Runtime \u00b7 " +
                       (py.exists() ? "\u25cf" : "\u25cb") + " Python \u00b7 " +
                       (hermes.exists() ? "\u25cf" : "\u25cb") + " Hermes \u00b7 " +
                       (bridge.exists() ? "\u25cf" : "\u25cb") + " Bridge \u00b7 " +
                       (history.exists() ? "\u25cf" : "\u25cb") + " History");
    }

    private void updateRuntimeStatusWithMessage() {
        ensureRuntime();
        updateRuntimeStatus();
        String py = runPythonVersion(5000);
        String imports = runHermesImportCheck(15000);
        String bridgeStatus = runBridgeCommand("--status");
        addBot("\ud83d\udcca Runtime Check\n\n" +
               status.getText().toString() + "\n\n" +
               "Python version:\n" + py + "\n\n" +
               "Hermes imports:\n" + imports + "\n\n" +
               "Bridge status:\n" + bridgeStatus,
               "He thong");
    }

    private File getRuntimeDir() {
        return new File(getFilesDir(), "hermes-runtime");
    }

    private ProcessBuilder basePythonProcess(List<String> args) {
        File rt = getRuntimeDir();
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.directory(rt);
        pb.environment().put("LD_LIBRARY_PATH", new File(rt, "lib").getAbsolutePath());
        // Do NOT set LD_PRELOAD — it causes Permission denied on Android/APK.
        // The bundled Python loads libpython3.13.so via DT_NEEDED when LD_LIBRARY_PATH is set.
        pb.environment().remove("LD_PRELOAD");
        pb.environment().put("PYTHONHOME", rt.getAbsolutePath());
        String paths = new File(rt, "lib/python3.13").getAbsolutePath() + ":" +
                       new File(rt, "lib/python3.13/site-packages").getAbsolutePath() + ":" +
                       new File(rt, "hermes-agent").getAbsolutePath();
        pb.environment().put("PYTHONPATH", paths);
        pb.environment().put("HERMES_HOME", getHermesHomeDir().getAbsolutePath());
        pb.environment().put("HOME", getHermesHomeDir().getAbsolutePath());
        pb.environment().put("NO_COLOR", "1");
        pb.environment().put("HERMES_TUI", "0");
        // API keys are injected via Android app settings dialog or bridge_settings.json
        return pb;
    }

    private ProcessBuilder basePythonProcess(String... args) {
        List<String> cmd = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) cmd.add(args[i]);
        return basePythonProcess(cmd);
    }

    private String readProcess(Process p) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) out.append(line).append("\n");
        while ((line = er.readLine()) != null) out.append(line).append("\n");
        int code = p.waitFor();
        if (code != 0) out.append("exit=").append(code).append("\n");
        return out.toString().trim();
    }

    private String readMergedProcess(Process p) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) out.append(line).append("\n");
        return out.toString().trim();
    }

    private String runPythonVersion(long timeoutMs) {
        try {
            File py = new File(getRuntimeDir(), "bin/python");
            if (!py.exists()) return "missing";
            ProcessBuilder pb = basePythonProcess(py.getAbsolutePath(), "--version");
            return readProcess(pb.start());
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private String runHermesImportCheck(long timeoutMs) {
        try {
            File py = new File(getRuntimeDir(), "bin/python");
            File check = new File(getRuntimeDir(), "hermes_import_check.py");
            if (!py.exists()) return "python missing";
            if (!check.exists()) return "hermes_import_check.py missing";
            ProcessBuilder pb = basePythonProcess(py.getAbsolutePath(), check.getAbsolutePath());
            String hermes = new File(getRuntimeDir(), "hermes-agent").getAbsolutePath();
            String paths = new File(getRuntimeDir(), "lib/python3.13").getAbsolutePath() + ":" +
                           new File(getRuntimeDir(), "lib/python3.13/site-packages").getAbsolutePath() + ":" + hermes;
            pb.environment().put("PYTHONPATH", paths);
            return readProcess(pb.start());
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private static class RuntimeInfo {
        String message;
        RuntimeInfo(String message) { this.message = message; }
    }

    private RuntimeInfo ensureRuntime() {
        File rt = getRuntimeDir();
        File manifest = new File(rt, "MANIFEST.txt");
        if (manifest.exists()) {
            return new RuntimeInfo("Runtime da ton tai: " + rt.getAbsolutePath());
        }
        try {
            unzipAsset("hermes-runtime.zip", rt);
            return new RuntimeInfo("Da giai nen hermes-runtime.zip vao: " + rt.getAbsolutePath());
        } catch (Exception e) {
            return new RuntimeInfo("Bootstrap runtime loi: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void unzipAsset(String assetName, File destDir) throws Exception {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new Exception("Khong tao duoc thu muc " + destDir.getAbsolutePath());
        }
        InputStream is = getAssets().open(assetName);
        ZipInputStream zis = new ZipInputStream(is);
        byte[] buffer = new byte[8192];
        ZipEntry entry;
        String canonicalRoot = destDir.getCanonicalPath() + File.separator;
        while ((entry = zis.getNextEntry()) != null) {
            File out = new File(destDir, entry.getName());
            String canonicalOut = out.getCanonicalPath();
            if (!canonicalOut.startsWith(canonicalRoot)) {
                throw new Exception("Zip entry khong an toan: " + entry.getName());
            }
            if (entry.isDirectory()) {
                out.mkdirs();
            } else {
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(out);
                int n;
                while ((n = zis.read(buffer)) > 0) fos.write(buffer, 0, n);
                fos.close();
                if (out.getName().endsWith(".py") ||
                    out.getAbsolutePath().contains(File.separator + "bin" + File.separator))
                    out.setExecutable(true, true);
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════
    //  SHIZUKU INTEGRATION
    // ═══════════════════════════════════════════

    private void initShizuku() {
        android.util.Log.d("DaidongAgent", "initShizuku called");
        try {
            Shizuku.addBinderReceivedListener(new Shizuku.OnBinderReceivedListener() {
                public void onBinderReceived() {
                    android.util.Log.d("DaidongAgent", "onBinderReceived listener triggered");
                    shizukuAvailable = true;
                    checkShizukuPermission();
                }
            });
            Shizuku.addBinderDeadListener(new Shizuku.OnBinderDeadListener() {
                public void onBinderDead() {
                    android.util.Log.d("DaidongAgent", "onBinderDead listener triggered");
                    shizukuAvailable = false;
                    shizukuGranted = false;
                    serviceBound = false;
                    serviceMessenger = null;
                }
            });
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    android.util.Log.d("DaidongAgent", "onRequestPermissionResult: requestCode=" + requestCode + ", grantResult=" + grantResult);
                    if (requestCode == SHIZUKU_REQUEST_CODE) {
                        shizukuGranted = (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED);
                        if (shizukuGranted) {
                            bindShizukuService();
                        }
                    }
                }
            });
            boolean ping = Shizuku.pingBinder();
            android.util.Log.d("DaidongAgent", "pingBinder returned: " + ping);
            if (ping) {
                shizukuAvailable = true;
                checkShizukuPermission();
            }
        } catch (Throwable t) {
            shizukuAvailable = false;
            android.util.Log.e("DaidongAgent", "Shizuku init failed", t);
            addBot("\u26a0 Shizuku init failed: " + t.getClass().getSimpleName() + " - " + t.getMessage(), "He thong");
        }
    }

    private void checkShizukuPermission() {
        int result = Shizuku.checkSelfPermission();
        android.util.Log.d("DaidongAgent", "checkShizukuPermission result: " + result);
        shizukuGranted = (result == android.content.pm.PackageManager.PERMISSION_GRANTED);
        if (!shizukuGranted) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                addBot("\ud83d\udd10 Can cap quyen Shizuku de dieu khien app. Dang yeu cau...", "He thong");
            }
            try {
                android.util.Log.d("DaidongAgent", "Requesting Shizuku permission...");
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
            } catch (Exception e) {
                android.util.Log.e("DaidongAgent", "Error requesting Shizuku permission", e);
                addBot("\u274c Loi request quyen Shizuku: " + e.getMessage(), "Loi");
            }
        } else {
            bindShizukuService();
        }
    }

    private void bindShizukuService() {
        android.util.Log.d("DaidongAgent", "bindShizukuService called, serviceBound=" + serviceBound);
        if (serviceBound) return;
        try {
            ComponentName cn = new ComponentName("com.daidong.hermesstandalone",
                "com.daidong.hermesstandalone.ShizukuUserService");
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(cn)
                .daemon(false)
                .version(1)
                .processNameSuffix("user_service")
                .debuggable(true);
            android.util.Log.d("DaidongAgent", "Binding Shizuku service with args: " + cn);
            Shizuku.bindUserService(args, shizukuServiceConnection);
        } catch (Exception e) {
            android.util.Log.e("DaidongAgent", "Error binding Shizuku user service", e);
            addBot("\u274c Loi bind Shizuku service: " + e.getMessage(), "Loi");
        }
    }

    private String execShizuku(String command) {
        if (!shizukuAvailable) return "\u274c Shizuku khong kha dung.";
        if (!shizukuGranted)   return "\u274c Chua cap quyen Shizuku. Vao app Shizuku de cap quyen.";
        if (!serviceBound || shizukuServiceBinder == null) return "\u274c Shizuku service chua san sang.";
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
            _data.writeString(command);
            shizukuServiceBinder.transact(12345, _data, _reply, 0);
            String result = _reply.readString();
            if (result != null) {
                return "\u2705 " + result;
            }
            return "\u274c Khong co ket qua tu Shizuku.";
        } catch (Exception e) {
            return "\u274c EXEC [" + command + "]\nLoi: " + e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            _data.recycle();
            _reply.recycle();
        }
    }

    private String extractExecBlock(String reply) {
        if (reply == null) return null;
        int start = reply.indexOf("[EXEC:");
        if (start == -1) return null;
        int end = reply.indexOf("]", start);
        if (end == -1) return null;
        return reply.substring(start + 6, end).trim();
    }

    private String processExecBlocks(String reply, String execBlock, String execResult) {
        if (reply == null) return "";
        int start = reply.indexOf("[EXEC:" + execBlock + "]");
        if (start == -1) return reply;
        int end = start + ("[EXEC:" + execBlock + "]").length();
        return reply.substring(0, start) + execResult + reply.substring(end);
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
