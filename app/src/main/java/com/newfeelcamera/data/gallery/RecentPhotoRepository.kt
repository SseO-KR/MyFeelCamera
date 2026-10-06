package com.newfeelcamera.data.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RecentPhotoRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    suspend fun latestPhotoUri(): Uri? = withContext(Dispatchers.IO) {
        val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection =
            "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND " +
                "${MediaStore.Images.Media.IS_PENDING} = 0"

        appContext.contentResolver.query(
            imageUri,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            arrayOf("Pictures/NewFeelCamera/", appContext.packageName),
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(imageUri, cursor.getLong(0))
            } else {
                null
            }
        }
    }
}
