package top.tobyprime.mcedia_mtv_plugin.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PublicChannelSortTest {

    @Test
    void nextCyclesThroughAllModes() {
        assertEquals(PublicChannelSort.CREATED_NEWEST, PublicChannelSort.RELEVANCE.next());
        assertEquals(PublicChannelSort.CREATED_OLDEST, PublicChannelSort.CREATED_NEWEST.next());
        assertEquals(PublicChannelSort.MOST_VIEWERS, PublicChannelSort.CREATED_OLDEST.next());
        assertEquals(PublicChannelSort.NAME_A_Z, PublicChannelSort.MOST_VIEWERS.next());
        assertEquals(PublicChannelSort.RELEVANCE, PublicChannelSort.NAME_A_Z.next());
    }

    @Test
    void displayNameReturnsChinese() {
        assertEquals("综合排序", PublicChannelSort.RELEVANCE.displayName());
        assertEquals("最新创建", PublicChannelSort.CREATED_NEWEST.displayName());
        assertEquals("最早创建", PublicChannelSort.CREATED_OLDEST.displayName());
        assertEquals("最多观众", PublicChannelSort.MOST_VIEWERS.displayName());
        assertEquals("名称 A-Z", PublicChannelSort.NAME_A_Z.displayName());
    }
}
