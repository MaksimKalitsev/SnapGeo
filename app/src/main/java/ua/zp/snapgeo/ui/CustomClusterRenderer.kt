package ua.zp.snapgeo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import ua.zp.snapgeo.data.ItemMarker

class CustomClusterRenderer(
    private val context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<ItemMarker>
) : DefaultClusterRenderer<ItemMarker>(context, map, clusterManager) {

    override fun onBeforeClusterItemRendered(item: ItemMarker, markerOptions: MarkerOptions) {
        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, item.photoPath)
        val smallMarker = Bitmap.createScaledBitmap(bitmap, 150, 150, false)
        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
        markerOptions.rotation(90f)
    }

    override fun shouldRenderAsCluster(cluster: Cluster<ItemMarker>): Boolean {
        // The number here (in this case 2) indicates how many close-by markers on the map should be
        // clustered into one. Adjust the number as per your need.
        return cluster.size > 2
    }
}


