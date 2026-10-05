package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SuggestionsControllerTest {
    @Before
    public void setUp() {
        Playlist.instance().clear();
    }

    @After
    public void tearDown() {
        Playlist.instance().clear();
    }

    @Test
    public void mixAutoplayPrefersInMixItemOverSection() throws Exception {
        Video current = video("current", "RDxxxx");
        Video mixNext = video("mixNext", "RDxxxx");
        current.nextMediaItem = SimpleMediaItem.from(mixNext);

        TestController controller = new TestController(current);
        controller.onNewVideo(current);

        Field field = SuggestionsController.class.getDeclaredField("mNextSectionVideo");
        field.setAccessible(true);
        field.set(controller, video("section", null));

        assertEquals("mixNext", controller.getNext().videoId);
    }

    @Test
    public void storedMixOrderWinsOverYouTubeNext() throws Exception {
        // YouTube rebuilds the mix around the current song, its next item may point back to the seed
        Video seed = video("seed", "RDseed");
        Video current = video("current", "RDseed");
        Video stored = video("stored", "RDseed");
        current.playlistInfo = mixInfo("RDseed");
        current.nextMediaItem = SimpleMediaItem.from(seed);

        TestController controller = new TestController(current);
        controller.onNewVideo(current);

        setField(controller, "mMixGroup", VideoGroup.from(new ArrayList<>(Arrays.asList(seed, current, stored))));
        setField(controller, "mMixPlaylistId", "RDseed");

        assertEquals("stored", controller.getNext().videoId);
    }

    private static void setField(SuggestionsController controller, String name, Object value) throws Exception {
        Field field = SuggestionsController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private static PlaylistInfo mixInfo(String playlistId) {
        return new PlaylistInfo() {
            @Override
            public String getTitle() {
                return "seed";
            }

            @Override
            public String getPlaylistId() {
                return playlistId;
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
                return 1;
            }
        };
    }

    private static Video video(String videoId, String playlistId) {
        Video video = new Video();
        video.videoId = videoId;
        video.playlistId = playlistId;
        return video;
    }

    private static class TestController extends SuggestionsController {
        private final PlaybackView mPlayer;
        private final Video mVideo;

        private TestController(Video video) {
            mPlayer = (PlaybackView) Proxy.newProxyInstance(
                    PlaybackView.class.getClassLoader(),
                    new Class<?>[]{PlaybackView.class},
                    (proxy, method, args) -> null);
            mVideo = video;
        }

        @Override
        public PlaybackView getPlayer() {
            return mPlayer;
        }

        @Override
        public Video getVideo() {
            return mVideo;
        }
    }
}
