package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CardSizingTest {
    @Test
    public void phonesAreNeverLarge() {
        // S24+ and similar: about 411dp on the narrow side, either orientation
        assertFalse(CardSizing.isLargeLandscape(false, 411, false));
        assertFalse(CardSizing.isLargeLandscape(true, 411, false));
    }

    @Test
    public void tabletsAndTvsAreLargeInLandscapeOnly() {
        assertTrue(CardSizing.isLargeLandscape(true, 800, false)); // tablet
        assertTrue(CardSizing.isLargeLandscape(true, 540, false)); // 1080p TV box at xhdpi
        assertTrue(CardSizing.isLargeLandscape(true, 411, true)); // anything reporting TV mode
        assertFalse(CardSizing.isLargeLandscape(false, 800, false)); // tablet in portrait
    }

    @Test
    public void bigScreensShowFewerBiggerCards() {
        // 1080p TV box at xhdpi: 960dp wide -> 3 cards plus a peek, each well above the phone's 160dp
        assertCardsAcross(960, 3);
        // 720p-class TV / tvdpi: 1280dp -> 4 plus a peek
        assertCardsAcross(1280, 4);
        // Big tablet landscape: 1366dp -> 4 plus a peek
        assertCardsAcross(1366, 4);
        // Never smaller than a phone card
        assertTrue(CardSizing.getRowCardWidthDp(500) >= CardSizing.ROW_CARD_DP);
    }

    @Test
    public void gridsGetMoreColumnsOnlyWhenTheyFit() {
        assertEquals(3, CardSizing.getGridColumns(960));
        assertEquals(4, CardSizing.getGridColumns(1280));
        assertEquals(2, CardSizing.getGridColumns(500));
    }

    private static void assertCardsAcross(int screenWidthDp, int fullCards) {
        float cardDp = CardSizing.getRowCardWidthDp(screenWidthDp);
        float rowDp = screenWidthDp - CardSizing.ROW_PADDING_DP;
        float across = rowDp / (cardDp + CardSizing.ROW_CARD_GAP_DP);

        assertEquals(fullCards + CardSizing.ROW_PEEK, across, 0.01f);
        assertTrue("card " + cardDp + "dp", cardDp > 250);
    }
}
