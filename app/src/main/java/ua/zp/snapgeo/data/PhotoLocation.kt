package ua.zp.snapgeo.data

import android.net.Uri

data class PhotoLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val photoUri: Uri
)
