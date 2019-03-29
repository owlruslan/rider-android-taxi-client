package com.github.owlruslan.rider.ui.modes.passanger.ride

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.transition.*
import androidx.viewpager.widget.PagerTitleStrip
import com.github.owlruslan.rider.R
import com.github.owlruslan.rider.di.ActivityScoped
import dagger.android.support.DaggerFragment
import javax.inject.Inject
import androidx.viewpager.widget.ViewPager
import com.github.owlruslan.rider.ui.modes.passanger.search.SearchFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.core.constants.Constants.PRECISION_6
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.*
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import dagger.Lazy
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_passanger_ride.*
import org.reactivestreams.Subscription
import timber.log.Timber
import java.util.concurrent.TimeUnit


@ActivityScoped
class RideFragment @Inject constructor() : DaggerFragment(), RideContract.View,
    OnMapReadyCallback {

    @Inject lateinit var presenter: RideContract.Presenter

    @set:Inject
    var searchFragmentProvider: Lazy<SearchFragment>? = null

    private lateinit var mapboxMap: MapboxMap
    private lateinit var currentRoute: DirectionsRoute
    private lateinit var client: MapboxDirections
    private lateinit var origin: Point
    private lateinit var destination: Point
    private lateinit var rootView: View
    private lateinit var sourceIconLayer: SymbolLayer
    private lateinit var pulseCircleLayer: SymbolLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter.takeView(this)
        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(requireContext(), getString(R.string.mapbox_access_token))
    }

    override fun onDestroyView() {
        client.cancelCall()
        super.onDestroyView()
    }

    override fun onDestroy() {
        presenter.dropView();  // prevent leaking activity in
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_passanger_ride, container, false)
        rootView = view
        presenter.addViewPager()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sceneRoot: ViewGroup = view.findViewById<View>(R.id.panelRoot) as ViewGroup
        val driverInfoScene: Scene = Scene.getSceneForLayout(sceneRoot, R.layout.driver_info_cardview, requireContext())

        requestButton.setOnClickListener {
            // 1. Draw the path to nearby car

            // Hide top navigation bar
            topNavigationInfoCardView.visibility = View.GONE

            // Set camera position to origin point
            val position = CameraPosition.Builder()
                .target(LatLng(origin.latitude(), origin.longitude()))
                .zoom(16.0)
                .build()
            mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 3000)
            // When searching nearby car
            val listener = {
                val markerAnimator = ValueAnimator()
                markerAnimator.setObjectValues(0f, 1f)
                markerAnimator.duration = 1000
                markerAnimator.addUpdateListener {
                    pulseCircleLayer.setProperties(
                        PropertyFactory.iconSize(10 * it.animatedValue as Float),
                        PropertyFactory.iconOpacity(1 - it.animatedValue as Float)
                    );
                }
                markerAnimator.repeatCount = ValueAnimator.INFINITE
                markerAnimator.repeatMode = ValueAnimator.RESTART
                markerAnimator.start()
            }

            mapboxMap.addOnCameraIdleListener(listener)

            /*
                    TODO:
                        1. Draw the path to nearby car.
                        2. Animate moving the car model to origin point.
                        3. Animate moving the car moving to destination point.
                        4. Rate the driver window
                        5. END!!!
                     */
            // 1. Draw the path to nearby car.
            Single.just(true)
                .delay(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({
                    // Stop searching animation
                    mapboxMap.removeOnCameraIdleListener(listener)
                    // Draw path from car to user
                    mapboxMap.setStyle(Style.MAPBOX_STREETS) {
                        destination = Point.fromLngLat(origin.longitude(), origin.latitude())
                        origin = Point.fromLngLat(49.124175, 55.789049)
                        initSource(it)
                        initLayers(it)
                        getRoute(it, origin, destination)
                    }

                }, {})


            // Transition to driver info
            TransitionManager.go(driverInfoScene, Slide().apply {

                addListener(object : Transition.TransitionListener {

                    override fun onTransitionEnd(transition: Transition) {

                        val bottomSheetDriverInfoCardView = rootView.findViewById<CardView>(R.id.bottomSheetDriverInfoCardView)
                        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetDriverInfoCardView)

                        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
                            override fun onStateChanged(bottomSheet: View, newState: Int) {}
                        })
                    }
                    override fun onTransitionResume(transition: Transition) {}
                    override fun onTransitionPause(transition: Transition) {}
                    override fun onTransitionCancel(transition: Transition) {}
                    override fun onTransitionStart(transition: Transition) {}
                })

            })
        }

        backButton.setOnClickListener {
            presenter.goToSearchView()
        }
        // Setup the MapView
        val map = this.childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        map.getMapAsync(this)
    }

    override fun showSearchView() {
        val rideFragment = searchFragmentProvider!!.get()
        activity!!.supportFragmentManager.beginTransaction()
            .setCustomAnimations(0, R.anim.slide_in_down)
            .replace(R.id.content_frame, rideFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun showViewPager() {
        val listDate = ArrayList<String>()
        listDate.add("Economy")
        listDate.add("Luxury")

        val viewPager = rootView.findViewById<ViewPager>(R.id.viewPager)
        viewPager.adapter = ViewPagerAdapter(requireContext(), listDate)

        val pagerTitleStrip = rootView.findViewById<PagerTitleStrip>(R.id.pagerTitleStrip)
        pagerTitleStrip.textSpacing = 8
    }

    /**
    * Add the route and marker sources to the map
    */
    private fun initSource(loadedMapStyle: Style) {

        // Source
        loadedMapStyle.addSource(
            GeoJsonSource(
                ROUTE_SOURCE_ID,
                FeatureCollection.fromFeatures(arrayOf())
            )
        )
        val iconGeoJsonSource = GeoJsonSource(
            ICON_SOURCE_ID,
            FeatureCollection.fromFeatures(
                arrayOf(
                    Feature.fromGeometry(
                        Point.fromLngLat(
                            origin.longitude(),
                            origin.latitude()
                        )
                    )
                )
            )
        )
        loadedMapStyle.addSource(iconGeoJsonSource)

        // Pulse Circle
        loadedMapStyle.addSource(
            GeoJsonSource(
                "source-pulse-circle",
                FeatureCollection.fromFeatures(arrayOf())
            )
        )
        val iconGeoJsonPulseCircle = GeoJsonSource(
            "icon-pulse-circle",
            FeatureCollection.fromFeatures(
                arrayOf(
                    Feature.fromGeometry(
                        Point.fromLngLat(
                            origin.longitude(),
                            origin.latitude()
                        )
                    )
                )
            )
        )
        loadedMapStyle.addSource(iconGeoJsonPulseCircle)

        // Destination
        loadedMapStyle.addSource(
            GeoJsonSource(
                ROUTE_DESTINATION_ID,
                FeatureCollection.fromFeatures(arrayOf())
            )
        )
        val iconGeoJsonDestination = GeoJsonSource(
            ICON_DESTINATION_ID,
            FeatureCollection.fromFeatures(
                arrayOf(
                    Feature.fromGeometry(
                        Point.fromLngLat(
                            destination.longitude(),
                            destination.latitude()
                        )
                    )
                )
            )
        )
        loadedMapStyle.addSource(iconGeoJsonDestination)
    }

    /**
     * Add the route and maker icon layers to the map
     */
    private fun initLayers(@NonNull loadedMapStyle: Style) {
        val routeLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID)

        // Add the LineLayer to the map. This layer will display the directions route.
        routeLayer.setProperties(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineColor(Color.parseColor("#0062FF"))
        )
        loadedMapStyle.addLayer(routeLayer)

        // Source
        // Add the source marker icon image to the map
        loadedMapStyle.addImage(
            SOURCE_PIN_ICON_ID, BitmapUtils.getBitmapFromDrawable(
                resources.getDrawable(R.drawable.ic_source, null)
            )!!
        )
        // Add the source marker icon SymbolLayer to the map
        sourceIconLayer = SymbolLayer("icon-source-layer-id", ICON_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(SOURCE_PIN_ICON_ID),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
        loadedMapStyle.addLayer(
            sourceIconLayer
        )

        // Pulse circle
        loadedMapStyle.addImage(
            "pulse-circle-image", BitmapUtils.getBitmapFromDrawable(
                resources.getDrawable(R.drawable.pulse_circle, null)
            )!!
        )
        pulseCircleLayer = SymbolLayer("icon-pulse-circle-layer-id", "icon-pulse-circle").withProperties(
            PropertyFactory.iconImage("pulse-circle-image"),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
        loadedMapStyle.addLayerBelow(pulseCircleLayer, "icon-source-layer-id")

        // Destination
        // Add the destination marker icon image to the map
        loadedMapStyle.addImage(
            DESTINATION_PIN_ICON_ID, BitmapUtils.getBitmapFromDrawable(
                resources.getDrawable(R.drawable.ic_destination, null)
            )!!
        )
        // Add the destination marker icon SymbolLayer to the map
        loadedMapStyle.addLayer(
            SymbolLayer("icon-destination-layer-id", ICON_DESTINATION_ID).withProperties(
                PropertyFactory.iconImage(DESTINATION_PIN_ICON_ID),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
    }

    /**
     * Make a request to the Mapbox Directions API. Once successful, pass the route to the
     * route layer.
     *
     * @param origin      the starting point of the route
     * @param destination the desired finish point of the route
     */
    private fun getRoute(@NonNull style: Style, origin: Point?, destination: Point?) {

        client = MapboxDirections.builder()
            .origin(origin!!)
            .destination(destination!!)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(DirectionsCriteria.PROFILE_DRIVING)
            .accessToken(getString(R.string.mapbox_access_token))
            .build()

        client.enqueueCall(object : Callback<DirectionsResponse> {
            override fun onResponse(call: Call<DirectionsResponse>, response: Response<DirectionsResponse>) {
                println(call.request().url().toString())

                // You can get the generic HTTP info about the response
                Timber.d("Response code: %s", response.code())
                if (response.body() == null) {
                    Timber.e("No routes found, make sure you set the right user and access token.")
                    return
                } else if (response.body()!!.routes().size < 1) {
                    Timber.e("No routes found")
                    return
                }

                // Get the directions route
                currentRoute = response.body()!!.routes()[0]

                // Make a toast which displays the route's distance
                Toast.makeText(
                    requireContext(), String.format(
                        "Distance: ",
                        currentRoute.distance()
                    ), Toast.LENGTH_SHORT
                ).show()


                if (style.isFullyLoaded) {
                    // Retrieve and update the source designated for showing the directions route
                    val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)

                    // Create a LineString with the directions route's geometry and
                    // reset the GeoJSON source for the route LineLayer source
                    if (source != null) {
                        Timber.d("onResponse: source != null")
                        source.setGeoJson(
                            FeatureCollection.fromFeature(
                                Feature.fromGeometry(LineString.fromPolyline(currentRoute.geometry()!!, PRECISION_6))
                            )
                        )
                        // Set route
                        val route = ArrayList<LatLng>()
                        val routeCoords = LineString.fromPolyline(currentRoute.geometry()!!, PRECISION_6).coordinates()
                        for (point: Point in routeCoords) {
                            route.add(LatLng(point.latitude(), point.longitude()))
                        }

                        val latLngBounds = LatLngBounds.Builder()
                            .includes(route)
                            .build()

                        val delta = 0.04

                        val latLngBounds2 = LatLngBounds.Builder()
                            .includes(route)
                            .include(LatLng(latLngBounds.latSouth - delta, latLngBounds.lonEast))
                            .build()

                        mapboxMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds2, 0, 200, 0, 0))
                    }
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, throwable: Throwable) {
                Timber.e("Error: %s", throwable.message)
                Toast.makeText(
                    requireContext(), "Error: " + throwable.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        mapboxMap.setStyle(Style.MAPBOX_STREETS) {
            this.mapboxMap = mapboxMap

            // Set the origin location to the Alhambra landmark in Granada, Spain.
            origin = Point.fromLngLat(49.124790, 55.789607)
            // Set the destination location to the Plaza del Triunfo in Granada, Spain.
            destination = Point.fromLngLat(49.125764, 55.796164)

            initSource(it)
            initLayers(it)

            // Get the directions route from the Mapbox Directions API
            getRoute(it, origin, destination)
        }
    }

    companion object {
            private val ROUTE_LAYER_ID = "route-layer-id"
            private val ROUTE_SOURCE_ID = "route-source-id"
            private val ROUTE_DESTINATION_ID = "route-destination-id"
            private val ICON_SOURCE_ID = "icon-source-id"
            private val ICON_DESTINATION_ID = "icon-destination-id"
            private val SOURCE_PIN_ICON_ID = "source-pin-icon-id"
            private val DESTINATION_PIN_ICON_ID = "destination-pin-icon-id"
    }
}