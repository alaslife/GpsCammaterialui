package com.alas.md3gpscam.ui.screens

import com.alas.md3gpscam.data.database.PhotoEntity
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

class PhotoClusterItem(
    val photo: PhotoEntity
) : ClusterItem {

    private val position: LatLng = LatLng(photo.latitude, photo.longitude)
    private val title: String = photo.address
    private val snippet: String = "Taken on ${photo.timestamp}"

    override fun getPosition(): LatLng {
        return position
    }

    override fun getTitle(): String {
        return title
    }

    override fun getSnippet(): String {
        return snippet
    }

    override fun getZIndex(): Float {
        return 0f
    }

    /**
     * Optional: Implement equals and hashCode to prevent items from being re-added on recomposition.
     */
    override fun equals(other: Any?): Boolean {
        if (other is PhotoClusterItem) {
            return other.photo.id == photo.id
        }
        return false
    }

    override fun hashCode(): Int {
        return photo.id.hashCode()
    }
}
