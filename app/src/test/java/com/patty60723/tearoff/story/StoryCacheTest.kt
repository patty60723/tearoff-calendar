package com.patty60723.tearoff.story

import com.patty60723.tearoff.Almanac
import com.patty60723.tearoff.AppClock
import com.patty60723.tearoff.CalendarWidget
import com.patty60723.tearoff.MainActivity
import com.patty60723.tearoff.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** P5-1：故事 assets 整個 process 只解析一次；頁數這類狀態還是每次讀，不會因為快取而過期 */
@RunWith(RobolectricTestRunner::class)
class StoryCacheTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun fresh() = StoryBook.Assets.clear()

    private fun parses(block: () -> Unit): Int {
        val before = StoryBook.Assets.parseCount
        block()
        return StoryBook.Assets.parseCount - before
    }

    @Test
    fun widgetUpdateParsesOnce() {
        StoryBook(context).jumpTo(14, AppClock.today(context))
        val page = Almanac.page(AppClock.today(context))
        // 一次小工具更新（Android 12 以上）會建 4 種版面；以前每個版面都 new StoryBook 重新解析，共 4 次
        assertEquals(1, parses { PageLayout.entries.forEach { CalendarWidget.build(context, page, it) } })
        // 之後的更新不再解析
        assertEquals(0, parses { PageLayout.entries.forEach { CalendarWidget.build(context, page, it) } })
    }

    @Test
    fun appParsesOnce() {
        StoryBook(context).jumpTo(5, AppClock.today(context))
        // App 本身、故事卡、日曆頁（小工具的 TALL 版面）、下一頁的快照：以前各自解析
        assertEquals(1, parses { Robolectric.buildActivity(MainActivity::class.java).setup() })
    }

    @Test
    fun everyBookSharesTheSameBeats() {
        assertSame(StoryBook(context).beats, StoryBook(context).beats)
    }

    @Test
    fun pageStateIsNotCached() {
        val today = AppClock.today(context)
        StoryBook(context).jumpTo(3, today)
        val three = StoryBook(context).parts
        assertEquals(StoryEngine.unlocked(StoryBook(context).beats, 3), three)
        StoryBook(context).jumpTo(14, today)
        val fourteen = StoryBook(context).parts
        assertEquals(StoryEngine.unlocked(StoryBook(context).beats, 14), fourteen)
        assertNotEquals(three, fourteen)
        assertEquals(14, StoryBook(context).page)
    }
}
