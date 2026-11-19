package com.example.lgremote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * ИК-пульт для телевизора LG 32LB530U (протокол NEC, адрес 0x04, несущая 38 кГц).
 * Использует встроенный ИК-порт телефона (Redmi Note 10 Pro) через ConsumerIrManager.
 */
public class MainActivity extends Activity {

    private static final int CARRIER_HZ = 38000;

    // ---- Коды LG (NEC, 32 бита, передаются старшим битом вперёд) ----
    private static final int POWER   = 0x20DF10EF;
    private static final int INPUT   = 0x20DFD02F;
    private static final int MUTE    = 0x20DF906F;
    private static final int VOL_UP  = 0x20DF40BF;
    private static final int VOL_DN  = 0x20DFC03F;
    private static final int CH_UP   = 0x20DF00FF;
    private static final int CH_DN   = 0x20DF807F;
    private static final int MENU    = 0x20DFC23D; // SETTINGS - главное меню
    private static final int QMENU   = 0x20DFA25D;
    private static final int UP      = 0x20DF02FD;
    private static final int DOWN    = 0x20DF827D;
    private static final int LEFT    = 0x20DFE01F;
    private static final int RIGHT   = 0x20DF609F;
    private static final int OK      = 0x20DF22DD;
    private static final int BACK    = 0x20DF14EB;
    private static final int EXIT    = 0x20DFDA25;
    private static final int[] DIGITS = {
            0x20DF08F7, 0x20DF8877, 0x20DF48B7, 0x20DFC837, 0x20DF28D7,
            0x20DFA857, 0x20DF6897, 0x20DFE817, 0x20DF18E7, 0x20DF9867
    };

    private static final int COLOR_BG     = Color.parseColor("#14161A");
    private static final int COLOR_KEY    = Color.parseColor("#2A2E36");
    private static final int COLOR_ACCENT = Color.parseColor("#3D6FE0");
    private static final int COLOR_POWER  = Color.parseColor("#D32F2F");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConsumerIrManager ir;
    private boolean hasIr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ir = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        hasIr = ir != null && ir.hasIrEmitter();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(16), dp(12), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Пульт LG 32LB530U");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView status = new TextView(this);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(4), 0, dp(8));
        if (hasIr) {
            status.setText("ИК-порт найден. Направьте верх телефона на телевизор.");
            status.setTextColor(Color.parseColor("#8BC34A"));
        } else {
            status.setText("На этом устройстве нет ИК-передатчика.");
            status.setTextColor(Color.parseColor("#FF8A80"));
        }
        root.addView(status);

        root.addView(row(
                key("Вкл/Выкл", POWER, false, COLOR_POWER),
                key("Вход", INPUT, false, COLOR_KEY),
                key("Без звука", MUTE, false, COLOR_KEY)));

        root.addView(row(
                key("Громк +", VOL_UP, true, COLOR_ACCENT),
                key("Канал +", CH_UP, true, COLOR_ACCENT)));
        root.addView(row(
                key("Громк −", VOL_DN, true, COLOR_ACCENT),
                key("Канал −", CH_DN, true, COLOR_ACCENT)));

        root.addView(row(
                key("Меню", MENU, false, COLOR_KEY),
                key("Q.Меню", QMENU, false, COLOR_KEY)));

        root.addView(row(spacer(), key("▲", UP, true, COLOR_KEY), spacer()));
        root.addView(row(key("◀", LEFT, true, COLOR_KEY), key("OK", OK, false, COLOR_ACCENT),
                key("▶", RIGHT, true, COLOR_KEY)));
        root.addView(row(spacer(), key("▼", DOWN, true, COLOR_KEY), spacer()));

        root.addView(row(
                key("Назад", BACK, false, COLOR_KEY),
                key("Выход", EXIT, false, COLOR_KEY)));

        root.addView(row(digit(1), digit(2), digit(3)));
        root.addView(row(digit(4), digit(5), digit(6)));
        root.addView(row(digit(7), digit(8), digit(9)));
        root.addView(row(spacer(), digit(0), spacer()));

        setContentView(scroll);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------- ИК ----------------

    /** Строит NEC-кадр: 9 мс + 4,5 мс заголовок, 32 бита, стоп-бит. Значения в микросекундах. */
    static int[] buildNec(int code) {
        int[] p = new int[2 + 32 * 2 + 1];
        int i = 0;
        p[i++] = 9000;
        p[i++] = 4500;
        for (int b = 31; b >= 0; b--) {
            p[i++] = 560;
            p[i++] = (((code >>> b) & 1) == 1) ? 1690 : 560;
        }
        p[i] = 560;
        return p;
    }

    private void send(int code) {
        if (!hasIr) {
            Toast.makeText(this, "ИК-передатчика нет на этом устройстве", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ir.transmit(CARRIER_HZ, buildNec(code));
            getWindow().getDecorView().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка передачи ИК", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- UI ----------------

    private Button key(String label, final int code, final boolean repeat, int color) {
        final Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        b.setBackground(d);

        b.setOnTouchListener(new View.OnTouchListener() {
            private Runnable repeater;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setAlpha(0.6f);
                        send(code);
                        if (repeat) {
                            repeater = new Runnable() {
                                @Override
                                public void run() {
                                    send(code);
                                    handler.postDelayed(this, 150);
                                }
                            };
                            handler.postDelayed(repeater, 450);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(1f);
                        if (repeater != null) {
                            handler.removeCallbacks(repeater);
                            repeater = null;
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        return b;
    }

    private Button digit(int n) {
        return key(String.valueOf(n), DIGITS[n], false, COLOR_KEY);
    }

    private View spacer() {
        return new View(this);
    }

    private LinearLayout row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(60), 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            r.addView(v, lp);
        }
        return r;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
