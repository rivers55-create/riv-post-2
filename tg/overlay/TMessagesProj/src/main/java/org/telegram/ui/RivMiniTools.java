package org.telegram.ui;

import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.RivPrefs;
import org.telegram.messenger.RivStore;
import org.telegram.messenger.RivT;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;

/** Riv Post: four small tools in one file: Calculator, Converter, Random picker, Countdown. */
public final class RivMiniTools {

    private RivMiniTools() {
    }

    // ------------------------------------------------------------------ calculator

    public static class Calculator extends RivToolScreen {
        private String expr = "";
        private String result = "";
        private final ArrayList<String> history = new ArrayList<>();

        @Override
        protected String title() {
            return RivT.t("Calculator", "الآلة الحاسبة");
        }

        @Override
        protected void build() {
            EditText edit = new EditText(ctx);
            edit.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            edit.setTextSize(22);
            edit.setHint("12 * (3 + 4) / 2");
            edit.setInputType(InputType.TYPE_CLASS_TEXT);
            edit.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14), AndroidUtilities.dp(18), AndroidUtilities.dp(14));
            edit.setText(expr);
            layout.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            addAction(RivT.t("Calculate", "احسب"), true, () -> {
                expr = edit.getText().toString();
                try {
                    double v = new Parser(expr).parse();
                    result = fmt(v);
                    history.add(0, expr + " = " + result);
                    if (history.size() > 15) history.remove(history.size() - 1);
                } catch (Throwable t) {
                    result = RivT.t("Can't read that", "لا يمكن قراءة هذا");
                }
                rebuild();
            });
            addText(result.isEmpty() ? "—" : result, false);
            if (!result.isEmpty()) addAction(RivT.t("Copy result", "نسخ النتيجة"), false, () -> copy(result));
            if (!history.isEmpty()) {
                addHeader(RivT.t("History", "السجل"));
                for (String h : history) {
                    final String line = h;
                    addCell(line, "", true, () -> {
                        expr = line.substring(0, line.lastIndexOf(" = "));
                        rebuild();
                    }, null);
                }
            }
            addInfo(RivT.t("Use + − * / ^ ( ) and %. Example: 200 + 15%", "استخدم + - * / ^ ( ) و %. مثال: 200 + 15%"));
        }

        static String fmt(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) return "—";
            if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
            String s = String.format(Locale.US, "%.10f", v);
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            return s;
        }

        /** + - * / ^ parentheses and a trailing % (x% of the left side after + or -). */
        static class Parser {
            final String s;
            int pos;

            Parser(String s) {
                this.s = s.replace(" ", "").replace("×", "*").replace("÷", "/").replace("−", "-").replace(",", ".");
            }

            double parse() {
                double v = expression();
                if (pos == s.length() - 1 && s.charAt(pos) == '%') {
                    pos++;
                    v = v / 100;
                }
                if (pos < s.length()) throw new IllegalArgumentException();
                return v;
            }

            double expression() {
                double v = term();
                while (pos < s.length()) {
                    char c = s.charAt(pos);
                    if (c == '+' || c == '-') {
                        pos++;
                        double r = term();
                        if (pos < s.length() && s.charAt(pos) == '%') {
                            pos++;
                            r = v * r / 100;
                        }
                        v = c == '+' ? v + r : v - r;
                    } else break;
                }
                return v;
            }

            double term() {
                double v = power();
                while (pos < s.length()) {
                    char c = s.charAt(pos);
                    if (c == '*' || c == '/') {
                        pos++;
                        double r = power();
                        v = c == '*' ? v * r : v / r;
                    } else break;
                }
                return v;
            }

            double power() {
                double b = unary();
                if (pos < s.length() && s.charAt(pos) == '^') {
                    pos++;
                    return Math.pow(b, power());
                }
                return b;
            }

            double unary() {
                if (pos < s.length() && s.charAt(pos) == '-') {
                    pos++;
                    return -unary();
                }
                if (pos < s.length() && s.charAt(pos) == '+') {
                    pos++;
                    return unary();
                }
                double v = atom();
                return v;
            }

            double atom() {
                if (pos < s.length() && s.charAt(pos) == '(') {
                    pos++;
                    double v = expression();
                    if (pos >= s.length() || s.charAt(pos) != ')') throw new IllegalArgumentException();
                    pos++;
                    return v;
                }
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                if (start == pos) throw new IllegalArgumentException();
                return Double.parseDouble(s.substring(start, pos));
            }
        }
    }

    // ------------------------------------------------------------------ converter

    public static class Converter extends RivToolScreen {
        private static final String[] CATS_EN = {"Length", "Weight", "Temperature", "Volume", "Area", "Speed", "Data", "Currency"};
        private static final String[] CATS_AR = {"الطول", "الوزن", "الحرارة", "الحجم", "المساحة", "السرعة", "البيانات", "العملات"};
        // name:factor-to-base ; temperature handled separately
        private static final String[][] UNITS = {
                {"m:1", "km:1000", "cm:0.01", "mm:0.001", "mile:1609.344", "yard:0.9144", "ft:0.3048", "in:0.0254"},
                {"kg:1", "g:0.001", "mg:0.000001", "lb:0.45359237", "oz:0.028349523", "ton:1000"},
                {"°C:0", "°F:0", "K:0"},
                {"L:1", "mL:0.001", "gal(US):3.785411784", "qt(US):0.946352946", "cup(US):0.2365882365", "fl oz(US):0.0295735296"},
                {"m²:1", "km²:1000000", "ha:10000", "ft²:0.09290304", "acre:4046.8564224", "mile²:2589988.110336"},
                {"m/s:1", "km/h:0.2777777778", "mph:0.44704", "knot:0.5144444444"},
                {"B:1", "KB:1024", "MB:1048576", "GB:1073741824", "TB:1099511627776", "bit:0.125"},
        };

        private int cat = 0;
        private int from = 0, to = 1;
        private String value = "1";
        private String cur1 = "USD", cur2 = "EUR";
        private String status = "";

        @Override
        protected String title() {
            return RivT.t("Unit & currency converter", "محول الوحدات والعملات");
        }

        private String[] unitNames() {
            String[] u = UNITS[cat];
            String[] out = new String[u.length];
            for (int i = 0; i < u.length; i++) out[i] = u[i].substring(0, u[i].lastIndexOf(':'));
            return out;
        }

        private double factor(int i) {
            String u = UNITS[cat][i];
            return Double.parseDouble(u.substring(u.lastIndexOf(':') + 1));
        }

        @Override
        protected void build() {
            boolean currency = cat == 7;
            addCell(RivT.t("Type", "النوع"), RivT.isArabic() ? CATS_AR[cat] : CATS_EN[cat], true, () -> choose(RivT.t("Type", "النوع"), RivT.isArabic() ? CATS_AR : CATS_EN, w -> {
                cat = w;
                from = 0;
                to = 1;
                status = "";
                rebuild();
            }), null);
            addCell(RivT.t("Value", "القيمة"), value, true, () -> ask(RivT.t("Value", "القيمة"), "1", value, false, v -> {
                value = v.trim();
                rebuild();
            }), null);
            if (currency) {
                addCell(RivT.t("From", "من"), cur1, true, () -> pickCurrency(true), null);
                addCell(RivT.t("To", "إلى"), cur2, true, () -> pickCurrency(false), null);
                addAction(RivT.t("Convert", "تحويل"), false, this::convertCurrency);
                if (!status.isEmpty()) addText(status, false);
                addInfo(RivT.t("Rates come from open.er-api.com when you tap Convert and are saved for offline use. They are mid-market rates, not what a bank gives you.",
                        "تأتي الأسعار من open.er-api.com عند الضغط على تحويل وتُحفظ للاستخدام دون اتصال. هي أسعار السوق المتوسطة وليست ما يعطيه البنك."));
                return;
            }
            String[] names = unitNames();
            addCell(RivT.t("From", "من"), names[from], true, () -> choose(RivT.t("From", "من"), names, w -> {
                from = w;
                rebuild();
            }), null);
            addCell(RivT.t("To", "إلى"), names[to], true, () -> choose(RivT.t("To", "إلى"), names, w -> {
                to = w;
                rebuild();
            }), null);
            String res;
            try {
                double v = Double.parseDouble(value.replace(",", "."));
                double out;
                if (cat == 2) out = temp(v, from, to);
                else out = v * factor(from) / factor(to);
                res = Calculator.fmt(round(out)) + " " + names[to];
            } catch (Throwable t) {
                res = "—";
            }
            addText(res, false);
            addAction(RivT.t("Copy result", "نسخ النتيجة"), false, () -> copy(Calculator.fmt(parseFirst(Converter.this.lastResult(names)))));
        }

        private String lastResult(String[] names) {
            try {
                double v = Double.parseDouble(value.replace(",", "."));
                return Calculator.fmt(round(cat == 2 ? temp(v, from, to) : v * factor(from) / factor(to)));
            } catch (Throwable t) {
                return "0";
            }
        }

        private static double parseFirst(String s) {
            try {
                return Double.parseDouble(s);
            } catch (Throwable t) {
                return 0;
            }
        }

        private static double round(double v) {
            return Math.round(v * 1e8) / 1e8;
        }

        private static double temp(double v, int from, int to) {
            double c = from == 0 ? v : from == 1 ? (v - 32) * 5 / 9 : v - 273.15;
            return to == 0 ? c : to == 1 ? c * 9 / 5 + 32 : c + 273.15;
        }

        private org.json.JSONObject rates() {
            try {
                return new org.json.JSONObject(RivPrefs.s("rivRates", "{}"));
            } catch (Throwable t) {
                return new org.json.JSONObject();
            }
        }

        private void pickCurrency(boolean first) {
            org.json.JSONObject r = rates();
            ArrayList<String> codes = new ArrayList<>();
            org.json.JSONObject rr = r.optJSONObject("rates");
            if (rr != null) {
                java.util.Iterator<String> it = rr.keys();
                while (it.hasNext()) codes.add(it.next());
                Collections.sort(codes);
            } else {
                Collections.addAll(codes, "USD", "EUR", "GBP", "JOD", "SAR", "AED", "EGP", "IQD", "TRY", "KWD", "QAR", "JPY");
            }
            choose(RivT.t("Currency", "العملة"), codes.toArray(new CharSequence[0]), w -> {
                if (first) cur1 = codes.get(w);
                else cur2 = codes.get(w);
                rebuild();
            });
        }

        private void convertCurrency() {
            status = RivT.t("Loading rates…", "جارٍ تحميل الأسعار…");
            rebuild();
            Utilities.globalQueue.postRunnable(() -> {
                String err = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://open.er-api.com/v6/latest/USD").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    org.json.JSONObject o = new org.json.JSONObject(bo.toString("UTF-8"));
                    if (o.optJSONObject("rates") != null) {
                        o.put("saved", System.currentTimeMillis());
                        RivPrefs.set("rivRates", o.toString());
                    }
                } catch (Throwable t) {
                    err = t.getMessage();
                }
                final String error = err;
                AndroidUtilities.runOnUIThread(() -> {
                    org.json.JSONObject rr = rates().optJSONObject("rates");
                    if (rr == null) {
                        status = RivT.t("No rates available. Check your connection.", "لا توجد أسعار. تحقق من الاتصال.");
                    } else {
                        try {
                            double v = Double.parseDouble(value.replace(",", "."));
                            double a = rr.getDouble(cur1), b = rr.getDouble(cur2);
                            status = Calculator.fmt(round(v / a * b)) + " " + cur2 + (error != null ? "\n" + RivT.t("(saved rates, could not refresh)", "(أسعار محفوظة، تعذر التحديث)") : "");
                        } catch (Throwable t) {
                            status = RivT.t("Pick two currencies and a number.", "اختر عملتين ورقماً.");
                        }
                    }
                    rebuild();
                });
            });
        }
    }

    // ------------------------------------------------------------------ random picker

    public static class RandomPicker extends RivToolScreen {
        private String result = "";
        private final Random rnd = new Random();

        @Override
        protected String title() {
            return RivT.t("Random picker", "الاختيار العشوائي");
        }

        @Override
        protected void build() {
            addAction(RivT.t("Flip a coin", "ارمِ عملة"), true, () -> {
                result = rnd.nextBoolean() ? RivT.t("Heads", "صورة") : RivT.t("Tails", "كتابة");
                rebuild();
            });
            addAction(RivT.t("Roll a die (6)", "ارمِ نرداً (6)"), true, () -> {
                result = String.valueOf(1 + rnd.nextInt(6));
                rebuild();
            });
            addAction(RivT.t("Number between…", "رقم بين…"), true, () -> ask(RivT.t("From, to (e.g. 1,100)", "من، إلى (مثال 1,100)"), "1,100", "1,100", false, v -> {
                try {
                    String[] p = v.split("[,\\s-]+");
                    int a = Integer.parseInt(p[0].trim()), b = Integer.parseInt(p[1].trim());
                    if (a > b) {
                        int t = a;
                        a = b;
                        b = t;
                    }
                    result = String.valueOf(a + rnd.nextInt(b - a + 1));
                } catch (Throwable t) {
                    result = RivT.t("Use two numbers, like 1,100", "استخدم رقمين مثل 1,100");
                }
                rebuild();
            }));
            addAction(RivT.t("Pick a name from a list", "اختر اسماً من قائمة"), true, () -> ask(RivT.t("One name per line", "اسم في كل سطر"), RivT.t("Ali\nSara\nOmar", "علي\nسارة\nعمر"), "", true, v -> {
                ArrayList<String> names = new ArrayList<>();
                for (String l : v.split("\n")) if (!l.trim().isEmpty()) names.add(l.trim());
                result = names.isEmpty() ? RivT.t("The list is empty", "القائمة فارغة") : names.get(rnd.nextInt(names.size()));
                rebuild();
            }));
            addAction(RivT.t("Split a list into 2 teams", "قسّم قائمة إلى فريقين"), false, () -> ask(RivT.t("One name per line", "اسم في كل سطر"), "", "", true, v -> {
                ArrayList<String> names = new ArrayList<>();
                for (String l : v.split("\n")) if (!l.trim().isEmpty()) names.add(l.trim());
                Collections.shuffle(names, rnd);
                StringBuilder a = new StringBuilder(RivT.t("Team 1: ", "الفريق 1: ")), b = new StringBuilder(RivT.t("Team 2: ", "الفريق 2: "));
                for (int i = 0; i < names.size(); i++) (i % 2 == 0 ? a : b).append(i < 2 ? "" : ", ").append(names.get(i));
                result = a + "\n" + b;
                rebuild();
            }));
            if (!result.isEmpty()) {
                addHeader(RivT.t("Result", "النتيجة"));
                addText(result, false).setTextSize(20);
                addAction(RivT.t("Copy", "نسخ"), false, () -> copy(result));
            }
        }
    }

    // ------------------------------------------------------------------ countdown

    public static class Countdown extends RivToolScreen {
        private static final String KEY = "rivCountdowns";

        @Override
        protected String title() {
            return RivT.t("Countdowns", "العد التنازلي");
        }

        static String remaining(long target) {
            long ms = target - System.currentTimeMillis();
            boolean past = ms < 0;
            ms = Math.abs(ms);
            long days = ms / 86400000L, hours = (ms / 3600000L) % 24, mins = (ms / 60000L) % 60;
            String s = days + RivT.t(" d ", " يوم ") + hours + RivT.t(" h ", " س ") + mins + RivT.t(" min", " د");
            return past ? RivT.t("passed ", "مضى ") + s : s;
        }

        @Override
        protected void build() {
            addAction(RivT.t("New countdown", "عد تنازلي جديد"), false, this::create);
            ArrayList<JSONObject> list = RivStore.load(KEY);
            if (list.isEmpty()) {
                addInfo(RivT.t("Make a countdown to a date, then copy or share the text.", "أنشئ عداً تنازلياً لتاريخ ثم انسخ النص أو شاركه."));
                return;
            }
            addHeader(RivT.t("Your countdowns", "عداداتك"));
            for (int i = 0; i < list.size(); i++) {
                final JSONObject o = list.get(i);
                final String line = "⏳ " + o.optString("title") + ": " + remaining(o.optLong("at"));
                addCell(o.optString("title"), remaining(o.optLong("at")), i != list.size() - 1, () -> choose(o.optString("title"), new CharSequence[]{RivT.t("Copy text", "نسخ النص"), RivT.t("Share", "مشاركة"), RivT.t("Delete", "حذف")}, w -> {
                    if (w == 0) copy(line);
                    else if (w == 1) {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.putExtra(Intent.EXTRA_TEXT, line);
                        ctx.startActivity(Intent.createChooser(share, title()));
                    } else {
                        ArrayList<JSONObject> all = RivStore.load(KEY);
                        for (int k = all.size() - 1; k >= 0; k--) if (all.get(k).optLong("id") == o.optLong("id")) all.remove(k);
                        RivStore.save(KEY, all);
                        rebuild();
                    }
                }), null);
            }
            addInfo(RivT.t("The text is a snapshot of the time left when you copy it.", "النص صورة للوقت المتبقي لحظة النسخ."));
        }

        private void create() {
            ask(RivT.t("What are you counting down to?", "إلى ماذا تعد تنازلياً؟"), RivT.t("Trip, exam, birthday…", "رحلة، امتحان، عيد ميلاد…"), "", false, name -> {
                if (name.trim().isEmpty() || getParentActivity() == null) return;
                Calendar c = Calendar.getInstance();
                new android.app.DatePickerDialog(getParentActivity(), (dp, y, m, d) -> {
                    new android.app.TimePickerDialog(getParentActivity(), (tp, h, mi) -> {
                        Calendar t = Calendar.getInstance();
                        t.set(y, m, d, h, mi, 0);
                        ArrayList<JSONObject> all = RivStore.load(KEY);
                        try {
                            JSONObject o = new JSONObject();
                            o.put("id", System.currentTimeMillis());
                            o.put("title", name.trim());
                            o.put("at", t.getTimeInMillis());
                            all.add(o);
                        } catch (Throwable ignored) {
                        }
                        RivStore.save(KEY, all);
                        rebuild();
                    }, 9, 0, true).show();
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            });
        }
    }
}
