package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Video card sizes by screen. Phones keep the fixed sizes of the layouts: 160dp cards in the
 * horizontal rows (two and a half across a portrait phone), two columns in the grids. A big
 * screen in landscape (tablet, Android TV box, a phone or tablet on a TV) would fit eight of those
 * small cards in a row, so there the cards grow instead: as many of about {@link #LARGE_CARD_DP}
 * as fit, the row showing a part of the next one so it still reads as scrollable.
 */
public final class CardSizing {
    /**
     * Narrowest side from which a screen counts as big. Tablets start at 600dp and TV boxes are
     * often 960x540dp (1080p at xhdpi) or 1280x720dp; phones are about 360-430dp.
     */
    static final int LARGE_SMALLEST_WIDTH_DP = 500;
    /** Card width big screens aim at */
    static final int LARGE_CARD_DP = 300;
    /** Phone row card, as in shared_video_card_row.xml */
    static final int ROW_CARD_DP = 160;
    /** Space after each row card (its layout_marginEnd) */
    static final int ROW_CARD_GAP_DP = 8;
    /** Row padding, start + end (shared_video_row.xml) */
    static final int ROW_PADDING_DP = 24;
    /** The part of the next card a row shows */
    static final float ROW_PEEK = 0.3f;
    static final int GRID_COLUMNS = 2;

    private CardSizing() {
    }

    public static boolean isLargeLandscape(Context context) {
        Configuration config = context.getResources().getConfiguration();
        boolean isTv = (config.uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;

        return isLargeLandscape(config.orientation == Configuration.ORIENTATION_LANDSCAPE, config.smallestScreenWidthDp, isTv);
    }

    /**
     * Width of a card in a horizontal row (Home and the other sections), in px.
     */
    public static int getRowCardWidthPx(Context context) {
        Configuration config = context.getResources().getConfiguration();
        float density = context.getResources().getDisplayMetrics().density;
        float widthDp = isLargeLandscape(context) ? getRowCardWidthDp(config.screenWidthDp) : ROW_CARD_DP;

        return Math.round(widthDp * density);
    }

    /**
     * Columns of the grids (Subscriptions and other grid sections, channel uploads, search).
     */
    public static int getGridColumns(Context context) {
        return isLargeLandscape(context) ? getGridColumns(context.getResources().getConfiguration().screenWidthDp) : GRID_COLUMNS;
    }

    static boolean isLargeLandscape(boolean isLandscape, int smallestWidthDp, boolean isTv) {
        return isLandscape && (smallestWidthDp >= LARGE_SMALLEST_WIDTH_DP || isTv);
    }

    /**
     * As many cards of about {@link #LARGE_CARD_DP} as fit the row (at least two), plus the peek.
     */
    static float getRowCardWidthDp(int screenWidthDp) {
        int rowDp = screenWidthDp - ROW_PADDING_DP;
        int fit = Math.max(2, rowDp / LARGE_CARD_DP);

        return Math.max(ROW_CARD_DP, rowDp / (fit + ROW_PEEK) - ROW_CARD_GAP_DP);
    }

    static int getGridColumns(int screenWidthDp) {
        return Math.max(GRID_COLUMNS, screenWidthDp / LARGE_CARD_DP);
    }
}
