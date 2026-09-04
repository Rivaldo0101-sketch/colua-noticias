package com.example.coluainformativa.utils;

import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class NavInsetHelper {

    /**
     * Aplica WindowInsets garantizando que la barra superior expanda su altura
     * para abarcar la barra de estado del sistema (statusBarInsets.top)
     * manteniendo el área útil del Toolbar idéntica y perfectamente centrada
     * en todas las pantallas.
     */
    public static void applySystemWindowInsets(View rootView, View appBarLayout, View bottomNavigationView, View scrollContentView) {
        if (rootView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            // 1. Ajuste de la barra superior (AppBarLayout): expande la altura total para albergar el status bar sin comprimir el contenido
            if (appBarLayout != null) {
                int baseHeaderHeightDp = (int) (72 * v.getResources().getDisplayMetrics().density);
                ViewGroup.LayoutParams lp = appBarLayout.getLayoutParams();
                if (lp != null) {
                    lp.height = baseHeaderHeightDp + statusBarInsets.top;
                    appBarLayout.setLayoutParams(lp);
                }
                appBarLayout.setPadding(
                        appBarLayout.getPaddingLeft(),
                        statusBarInsets.top,
                        appBarLayout.getPaddingRight(),
                        appBarLayout.getPaddingBottom()
                );
            }

            // 2. Altura de BottomNavigationView extendida para íconos + etiquetas grandes
            if (bottomNavigationView != null) {
                int densityHeight = (int) (80 * v.getResources().getDisplayMetrics().density);
                ViewGroup.LayoutParams lp = bottomNavigationView.getLayoutParams();
                if (lp != null) {
                    lp.height = densityHeight + navBarInsets.bottom;
                    bottomNavigationView.setLayoutParams(lp);
                }
                bottomNavigationView.setPadding(
                        bottomNavigationView.getPaddingLeft(),
                        bottomNavigationView.getPaddingTop(),
                        bottomNavigationView.getPaddingRight(),
                        navBarInsets.bottom
                );
            }

            // 3. Padding inferior al contenedor de contenido
            if (scrollContentView != null) {
                int extraBottomPadding = navBarInsets.bottom + (int) (96 * v.getResources().getDisplayMetrics().density);
                scrollContentView.setPadding(
                        scrollContentView.getPaddingLeft(),
                        scrollContentView.getPaddingTop(),
                        scrollContentView.getPaddingRight(),
                        extraBottomPadding
                );
            }

            return insets;
        });
    }
}
