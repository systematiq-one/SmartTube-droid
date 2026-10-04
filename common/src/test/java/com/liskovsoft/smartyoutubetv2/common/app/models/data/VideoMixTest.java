package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VideoMixTest {
    @Test
    public void mixIdIsSeededBySong() {
        assertEquals("RDkJQP7kiw5Fk", Video.createMixPlaylistId("kJQP7kiw5Fk"));
        assertNull(Video.createMixPlaylistId(null));
    }

    @Test
    public void songMixIsDetected() {
        Video video = withPlaylist("RDkJQP7kiw5Fk");

        assertTrue(video.isPlayingYouTubeMix());
        assertTrue(video.isPlayingSongMix());
    }

    @Test
    public void otherMixesAreMixesButNotSongMixes() {
        Video myMix = withPlaylist("RDMM");
        Video albumRadio = withPlaylist("RDCLAK5uy_kmPRjHDECIcuVwnKsx2Ng7fyNgFKWNJFs");

        assertTrue(myMix.isPlayingYouTubeMix());
        assertFalse(myMix.isPlayingSongMix());
        assertTrue(albumRadio.isPlayingYouTubeMix());
        assertFalse(albumRadio.isPlayingSongMix());
    }

    @Test
    public void regularPlaylistsAreNotMixes() {
        assertFalse(withPlaylist("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI").isPlayingYouTubeMix());
        assertFalse(withPlaylist(null).isPlayingYouTubeMix());
        assertFalse(new Video().isPlayingYouTubeMix());
    }

    private static Video withPlaylist(String playlistId) {
        Video video = new Video();
        video.playlistInfo = new PlaylistInfo() {
            @Override
            public String getPlaylistId() {
                return playlistId;
            }

            @Override
            public String getTitle() {
                return "Luis Fonsi - Despacito ft. Daddy Yankee";
            }

            @Override
            public boolean isSelected() {
                return false;
            }

            @Override
            public int getSize() {
                return -1;
            }

            @Override
            public int getCurrentIndex() {
                return 0;
            }
        };
        return video;
    }
}
