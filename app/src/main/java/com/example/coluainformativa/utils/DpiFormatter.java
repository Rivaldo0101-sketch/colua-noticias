package com.example.coluainformativa.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class DpiFormatter {

    public static void applyDpiFormatting(final EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String digits = s.toString().replaceAll("\\D", "");
                if (digits.length() > 13) {
                    digits = digits.substring(0, 13);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < digits.length(); i++) {
                    if (i == 4 || i == 9) {
                        formatted.append(" ");
                    }
                    formatted.append(digits.charAt(i));
                }

                String formattedStr = formatted.toString();
                if (!formattedStr.equals(s.toString())) {
                    editText.setText(formattedStr);
                    editText.setSelection(formattedStr.length());
                }

                isFormatting = false;
            }
        });
    }

    public static boolean isValidDpi(String dpi) {
        if (dpi == null) return false;
        String digits = dpi.replaceAll("\\D", "");
        return digits.length() == 13;
    }
}
