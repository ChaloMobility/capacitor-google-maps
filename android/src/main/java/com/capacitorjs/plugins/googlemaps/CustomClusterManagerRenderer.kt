package com.capacitorjs.plugins.googlemaps

import BusesMarker
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

class CustomClusterManagerRenderer(
    private val context: Context,
    private val map: GoogleMap,
    clusterManager: ClusterManager<CapacitorGoogleMapMarker>
) : DefaultClusterRenderer<CapacitorGoogleMapMarker>(context, map, clusterManager) {
    interface ClusterRenderListener {
        fun onClusterRenderPassStarted(generation: Int, expectedRenderedItemKeys: Set<String>)
        fun onClusterRenderPassSettled(generation: Int, expectedRenderedItemKeys: Set<String>)
    }

    private val clusterColor = Color.parseColor("#FE7C00")
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clusterRenderListener: ClusterRenderListener? = null
    private var renderGeneration: Int = 0
    private var currentExpectedRenderedItemKeys: Set<String> = emptySet()
    private val renderedItemKeysForPass = mutableSetOf<String>()
    private var settleRunnable: Runnable? = null

    fun setClusterRenderListener(listener: ClusterRenderListener?) {
        clusterRenderListener = listener
    }

    override fun onClustersChanged(clusters: Set<Cluster<CapacitorGoogleMapMarker>>) {
        renderGeneration += 1
        renderedItemKeysForPass.clear()
        currentExpectedRenderedItemKeys = clusters
            .filter { it.size == 1 }
            .flatMap { cluster -> cluster.items.map(::getClusterItemKey) }
            .toSet()

        clusterRenderListener?.onClusterRenderPassStarted(
            renderGeneration,
            currentExpectedRenderedItemKeys
        )

        super.onClustersChanged(clusters)
        scheduleSettleCheck(renderGeneration)
    }

    override fun onBeforeClusterItemRendered(item: CapacitorGoogleMapMarker, markerOptions: MarkerOptions) {
        item.markerOptions?.let {
            markerOptions.position(it.position)
            markerOptions.title(it.title)
            markerOptions.snippet(it.snippet)
            markerOptions.alpha(it.alpha)
            markerOptions.flat(it.isFlat)
            markerOptions.draggable(it.isDraggable)
            markerOptions.rotation(it.rotation)
            markerOptions.anchor(it.anchorU, it.anchorV)
            markerOptions.zIndex(it.zIndex)
        }

        val iconUrl = item.iconUrl

        when {
            iconUrl?.contains("buses_custom_marker") == true -> {
                val busesMarker = BusesMarker(context)
                markerOptions.icon(busesMarker.getMarkerIcon(item.getTitle(), iconUrl))
            }
            iconUrl?.contains("alert_custom_marker") == true -> {
                val alertMarker = AlertBusMarker(context)
                markerOptions.icon(alertMarker.getMarkerIcon(item.getTitle(), item.getSnippet(), iconUrl))
                markerOptions.title("")
            }
            iconUrl?.contains("alert_stop_custom_marker") == true -> {
                val alertStopMarker = AlertStopCustomMarker(context)
                markerOptions.icon(alertStopMarker.getMarkerIcon(item.getTitle(), item.getSnippet(), iconUrl))
                markerOptions.title("")
            }
            iconUrl?.contains("route_name") == true -> {
                val routeNameMarker = RouteNameMarker(context)
                markerOptions.icon(routeNameMarker.getMarkerIcon(item.getTitle()))
                markerOptions.anchor(0.5f, 1.0f)
                markerOptions.zIndex(1000f)
                markerOptions.title("")
            }
            iconUrl?.contains("overspeed_marker") == true -> {
                val overSpeedMarker = OverSpeedCustomMarker(context)
                val resId = context.resources.getIdentifier(
                    iconUrl,
                    "drawable",
                    context.packageName
                )
                markerOptions.icon(overSpeedMarker.getMarkerIcon(item.getTitle(), item.getSnippet(), resId))
                markerOptions.title("")
            }
            iconUrl?.contains("new_3d_marker") == true -> {
                val generator = DynamicMarkerGenerator(context)
                val safeColor = if (!item.markerBgColor.isNullOrEmpty()) {
                    try { Color.parseColor(item.markerBgColor) } catch (e: IllegalArgumentException) { Color.GRAY }
                } else {
                    Color.GRAY
                }
                val descriptor = generator.generateMarker(
                    busIconRes = R.drawable.ic_bus_white,
                    statusColor = safeColor,
                    angle = item.bearingAngle
                )
                markerOptions.icon(descriptor)
                markerOptions.anchor(
                    generator.getAnchor().first,
                    generator.getAnchor().second
                )
            }
            else -> {
                // Fallback: use the icon already set via getMarkerOptionsUpdated()
                item.markerOptions?.let {
                    if (it.icon != null) {
                        markerOptions.icon(it.icon)
                    }
                }
            }
        }

        // Set info window adapter based on infoIcon
        if (!item.infoIcon.isNullOrEmpty() && item.infoIcon != "not_show_info_window") {
            when {
                item.infoIcon == "buses_info_icon" -> {
                    map.setInfoWindowAdapter(BusesMarkerInfoWindow(context))
                }
                item.infoIcon?.contains("bus_alert_info") == true -> {
                    map.setInfoWindowAdapter(AlertMarkerInfoWindow(context))
                }
                item.infoIcon?.contains("last_updated_info") == true -> {
                    map.setInfoWindowAdapter(LastUpdatedInfoWindowAdapter(context))
                }
                item.infoIcon?.contains("stop_arrival_info") == true -> {
                    map.setInfoWindowAdapter(StopArrivalInfoWindowAdapter(context))
                }
                item.infoIcon?.contains("replay_info_icon") == true -> {
                    map.setInfoWindowAdapter(HistoryReplayInfoWindowAdapter(context))
                }
                else -> {
                    map.setInfoWindowAdapter(CustomInfoWindowAdapter(context))
                }
            }
        }
    }

    override fun onClusterItemRendered(item: CapacitorGoogleMapMarker, marker: Marker) {
        super.onClusterItemRendered(item, marker)
        marker.tag = item
        item.googleMapMarker = marker
        markClusterItemRendered(item)
    }

    override fun onClusterItemUpdated(item: CapacitorGoogleMapMarker, marker: Marker) {
        marker.tag = item
        item.googleMapMarker = marker
        markClusterItemRendered(item)
        marker.rotation = item.markerOptions?.rotation ?: marker.rotation

        if (item.infoData?.optBoolean("showInfoIcon") == true) {
            marker.showInfoWindow()
        } else {
            marker.hideInfoWindow()
        }

        val iconUrl = item.iconUrl

        when {
            iconUrl?.contains("buses_custom_marker") == true -> {
                val busesMarker = BusesMarker(context)
                val newIcon = busesMarker.getMarkerIcon(item.getTitle(), iconUrl)
                marker.setIcon(newIcon)
            }
            iconUrl?.contains("alert_custom_marker") == true -> {
                val alertMarker = AlertBusMarker(context)
                val newIcon = alertMarker.getMarkerIcon(item.getTitle(), item.getSnippet(), iconUrl)
                marker.setIcon(newIcon)
            }
            iconUrl?.contains("alert_stop_custom_marker") == true -> {
                val alertStopMarker = AlertStopCustomMarker(context)
                val newIcon = alertStopMarker.getMarkerIcon(item.getTitle(), item.getSnippet(), iconUrl)
                marker.setIcon(newIcon)
            }
            iconUrl?.contains("route_name") == true -> {
                val routeNameMarker = RouteNameMarker(context)
                val newIcon = routeNameMarker.getMarkerIcon(item.getTitle())
                marker.setIcon(newIcon)
            }
            iconUrl?.contains("overspeed_marker") == true -> {
                val overSpeedMarker = OverSpeedCustomMarker(context)
                val resId = context.resources.getIdentifier(
                    iconUrl,
                    "drawable",
                    context.packageName
                )
                val newIcon = overSpeedMarker.getMarkerIcon(item.getTitle(), item.getSnippet(), resId)
                marker.setIcon(newIcon)
            }
            iconUrl?.contains("new_3d_marker") == true -> {
                val generator = DynamicMarkerGenerator(context)
                val safeColor = if (!item.markerBgColor.isNullOrEmpty()) {
                    try { Color.parseColor(item.markerBgColor) } catch (e: IllegalArgumentException) { Color.GRAY }
                } else {
                    Color.GRAY
                }
                val descriptor = generator.generateMarker(
                    busIconRes = R.drawable.ic_bus_white,
                    statusColor = safeColor,
                    angle = item.bearingAngle
                )
                marker.setIcon(descriptor)
            }
            else -> {
                // Fallback: use the icon from markerOptions if available
                item.markerOptions?.icon?.let {
                    marker.setIcon(it)
                }
            }
        }
    }

    override fun getColor(clusterSize: Int): Int {
        return clusterColor
    }

    private fun markClusterItemRendered(item: CapacitorGoogleMapMarker) {
        val itemKey = getClusterItemKey(item)
        if (!currentExpectedRenderedItemKeys.contains(itemKey)) {
            return
        }

        renderedItemKeysForPass.add(itemKey)
        scheduleSettleCheck(renderGeneration)
    }

    private fun scheduleSettleCheck(generation: Int) {
        settleRunnable?.let(mainHandler::removeCallbacks)

        val runnable = Runnable {
            if (generation != renderGeneration) {
                return@Runnable
            }

            if (renderedItemKeysForPass.containsAll(currentExpectedRenderedItemKeys)) {
                clusterRenderListener?.onClusterRenderPassSettled(
                    generation,
                    currentExpectedRenderedItemKeys
                )
            }
        }

        settleRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun getClusterItemKey(item: CapacitorGoogleMapMarker): String {
        return item.getMarkerId()
            ?: "${item.position.latitude}:${item.position.longitude}:${item.title}:${item.snippet}"
    }
}
