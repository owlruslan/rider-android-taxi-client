package com.github.owlruslan.rider.ui.modes.passanger.ride

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.core.constants.Constants
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import io.reactivex.Observable
import android.animation.TypeEvaluator
import android.util.Log
import android.view.animation.LinearInterpolator
import com.github.owlruslan.rider.services.map.mapbox.MapboxService
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.sources.Source
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit


object MapboxAnimation {

    private const val SEARCH_ANIMATION_DURATION: Long = 1000
    private const val CAR_ANIMATION_DURATION: Long = 300
    private const val PULSE_CIRCLE_MULTIPLIER = 10
    private const val MIN_PROPERTY_VALUE = 0f
    private const val MAX_PROPERTY_VALUE = 1f

    lateinit var searchMarkerAnimator: ValueAnimator
    lateinit var cameraIdleListener: MapboxMap.OnCameraIdleListener

    private lateinit var carIconAnimator: ValueAnimator
    private var carIconCurrentLocation: LatLng? = null

    private fun createCameraPosition(point: LatLng, zoomValue: Double): CameraPosition =
        CameraPosition.Builder()
            .target(point)
            .zoom(zoomValue)
            .build()

    private fun animateCameraToPoint(point: Point, zoom: Double, animationTime: Int, map: MapboxMap) {
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                createCameraPosition(
                    LatLng(point.latitude(), point.longitude()),
                    zoom
                )
            ),
            animationTime
        )
    }

    fun animateSearch(
        point: Point,
        zoom: Double,
        animationTime: Int,
        pulseCircleLayerId: String,
        map: MapboxMap
    ) {
        animateCameraToPoint(point, zoom, animationTime, map)

        // Searching nearby car
        cameraIdleListener = MapboxMap.OnCameraIdleListener {
            searchMarkerAnimator = ValueAnimator()
            searchMarkerAnimator.setObjectValues(MIN_PROPERTY_VALUE, MAX_PROPERTY_VALUE)
            searchMarkerAnimator.duration = SEARCH_ANIMATION_DURATION
            searchMarkerAnimator.addUpdateListener {

                map.style?.getLayer(pulseCircleLayerId)?.setProperties(
                    PropertyFactory.iconSize(PULSE_CIRCLE_MULTIPLIER * it.animatedValue as Float),
                    PropertyFactory.iconOpacity(1 - it.animatedValue as Float)
                )
            }
            searchMarkerAnimator.repeatCount = ValueAnimator.INFINITE
            searchMarkerAnimator.repeatMode = ValueAnimator.RESTART
            searchMarkerAnimator.start()
        }

        map.addOnCameraIdleListener(cameraIdleListener)
    }

    fun stopAnimateSearch(map: MapboxMap) {
        map.removeOnCameraIdleListener(cameraIdleListener)
        searchMarkerAnimator.cancel()
    }

    fun animateCarMoving(style: Style, mapboxService: MapboxService) {
        val coordinatesList = LineString.fromPolyline(mapboxService.currentRoute.geometry()!!, Constants.PRECISION_6)
            .coordinates()
        var count = 0
        val observable: Observable<Point> = Observable.fromIterable(coordinatesList)
        observable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({point: Point? ->
                if (coordinatesList.size - 1 > count) {
                    var nextLocation = coordinatesList.get(count + 1)

                    if (carIconAnimator != null && carIconAnimator.isStarted) {
                        carIconCurrentLocation = carIconAnimator.animatedValue as LatLng
                        carIconAnimator.cancel()
                    }

                    if (latLngEvaluator != null) {
                        carIconAnimator = ObjectAnimator
                            .ofObject(latLngEvaluator,
                                if (count == 0) LatLng(RideFragment.CAR_LATITUDE, RideFragment.CAR_LONGITUDE)
                                else carIconCurrentLocation, LatLng(nextLocation.latitude(), nextLocation.longitude()))
                            .setDuration(CAR_ANIMATION_DURATION)
                        carIconAnimator.interpolator = LinearInterpolator()
                        carIconAnimator.addUpdateListener {
                            val animatedPosition = it.animatedValue as LatLng
                            mapboxService.addSource(
                                style,
                                MapboxService.CAR_ICON_SOURCE_ID,
                                Point.fromLngLat(
                                    animatedPosition.longitude, animatedPosition.latitude
                                )
                            )
                        }
                        carIconAnimator.start()

                        count++
                    }
                }
/*
            if (point != null) {
                Log.d("F", "F $point")
                carIconAnimator = ObjectAnimator
                    .ofObject(latLngEvaluator, LatLng(point.latitude(), point.longitude()), LatLng(point.latitude(), point.longitude()))
                    .setDuration(CAR_ANIMATION_DURATION)
                carIconAnimator.interpolator = LinearInterpolator()
                carIconAnimator.addUpdateListener {
                     val animatedPosition = it.animatedValue as LatLng
                     mapboxService.addSource(
                         style,
                         MapboxService.CAR_ICON_SOURCE_ID,
                         Point.fromLngLat(
                             animatedPosition.longitude, animatedPosition.latitude
                         )
                     )
                 }
                carIconAnimator.start()
            }*/

        }, {}).isDisposed
    }

    private val latLngEvaluator = object : TypeEvaluator<LatLng> {

        private val latLng = LatLng()

        override fun evaluate(fraction: Float, startValue: LatLng, endValue: LatLng): LatLng {
            latLng.latitude = startValue.latitude + (endValue.latitude - startValue.latitude) * fraction
            latLng.longitude = startValue.longitude + (endValue.longitude - startValue.longitude) * fraction
            return latLng
        }
    }
}