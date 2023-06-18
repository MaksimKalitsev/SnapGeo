package ua.zp.snapgeo.data

import android.net.Uri
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

data class ItemMarker(
    val latLng: LatLng,
    val myTitle: String?,
    val mySnippet: String?,
    val photoPath: Uri
) : ClusterItem {
    override fun getPosition() = latLng

    override fun getTitle() = myTitle

    override fun getSnippet() = mySnippet
    override fun getZIndex(): Float? {
        return null
    }
}
