package com.yaroslavgamayunov.toodoo

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaroslavgamayunov.toodoo.pageobjects.MainPage
import com.yaroslavgamayunov.toodoo.ui.MainActivity
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainPageTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    lateinit var mockServer: MockWebServer

    @Before
    fun initMockWebServer() {
        mockServer = MockWebServer()
        mockServer.start(TestConstants.MOCKSERVER_ADDRESS, TestConstants.MOCKSERVER_PORT)
    }

    @After
    fun shutdownMockWebServer() {
        mockServer.shutdown()
    }

    @Test
    fun testMainPage_checkBoxIsChecked_whenTaskIsClicked() {
        MainPage.performActionsOnTaskListItem(0, click())

        val hasCheckedCheckBox = hasDescendant(
            allOf(
                withId(R.id.completedTaskCheckBox),
                isChecked())
        )

        MainPage.checkListItem(0, hasCheckedCheckBox)
    }

    @Test
    fun testMainPage_taskItemDisappears_whenSwipedLeft() {
        val taskMatcher = hasDescendant(allOf(withText("Delete"), isDisplayed()))

        MainPage.checkItemPresence(taskMatcher, isPresent = true)
        MainPage.performActionsOnTaskListItem("Delete", swipeLeft())
        MainPage.checkItemPresence(taskMatcher, isPresent = false)
    }
}