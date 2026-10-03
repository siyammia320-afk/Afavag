package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.ExtensionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ExtenBrowser", appName)
    }

    @Test
    fun `insert and retrieve extension in database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val ext = ExtensionEntity(
            id = "test_ext_1",
            name = "Test Extension",
            version = "1.0",
            description = "Test description",
            installPath = "/data/data/com.example/files/extensions/test_ext_1"
        )
        db.extensionDao().insertOrUpdate(ext)
        val retrieved = db.extensionDao().getExtensionById("test_ext_1")
        assertNotNull(retrieved)
        assertEquals("Test Extension", retrieved?.name)
    }
}
