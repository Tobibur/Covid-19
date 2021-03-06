package com.tobibur.covid_19.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.tobibur.covid_19.R
import com.tobibur.covid_19.db.Cache
import com.tobibur.covid_19.model.CountriesStat
import com.tobibur.covid_19.network.Outcome
import com.tobibur.covid_19.utils.ItemClickListener
import com.tobibur.covid_19.utils.gone
import com.tobibur.covid_19.utils.toast
import com.tobibur.covid_19.view.adapters.StatsAdapter
import kotlinx.android.synthetic.main.home_fragment.*
import kotlinx.android.synthetic.main.stats_view_item.view.*
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.*

class HomeFragment : Fragment(), ItemClickListener {

    companion object {
        fun newInstance() = HomeFragment()

        private const val TAG = "HomeFragment"
    }

    private var countryName: String? = null
    private val homeViewModel: HomeViewModel by viewModel()
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var statsAdapter: StatsAdapter? = null

    lateinit var updatedAt: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.home_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

//        (activity as AppCompatActivity).supportActionBar?.setLogo(R.drawable.gocorona_logo)

        initRecyclerView()
        getLocationPermission()
        getTotalStats()
        getStatsFromApi()

        txtViewAll.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_viewAllStatsFragment)
        }
    }

    private fun getTotalStats() {
        homeViewModel.getWorldStat().observe(viewLifecycleOwner, Observer { outcome ->
            when (outcome) {
                is Outcome.Success -> {
                    Log.d(TAG, "onActivityCreated: ${outcome.data}")
                    txtWorldTotalCases.text = outcome.data.totalCases
                    txtTotalDeaths.text = "Deaths\n" + outcome.data.totalDeaths
                    txtTotalRecovered.text = "Recovered\n" + outcome.data.totalRecovered
                }
                is Outcome.Failure -> {
                    activity!!.toast(activity!!.getString(R.string.error_loading))
                }
            }
        })
    }

    private fun getStatsFromApi() {
        homeViewModel.getCases().observe(viewLifecycleOwner, Observer { outcome ->
            homeProgress.gone()
            when (outcome) {
                is Outcome.Success -> {
                    Log.d(TAG, "onActivityCreated: ${outcome.data}")
                    updatedAt = outcome.data.statisticTakenAt
                    fillListUI(outcome.data.countriesStat.take(10))
                    countryName = Cache(activity!!).getData(Cache.COUNTRY_NAME)
                    if (countryName != null) {
                        val countryCase =
                            outcome.data.countriesStat.filter { it.countryName.toLowerCase() == countryName!!.toLowerCase() }
                        if (countryCase.isNotEmpty()) {
                            includedItemCountry.txtCountryName.text = countryCase[0].countryName
                            includedItemCountry.txtCases.text = countryCase[0].cases
                            includedItemCountry.setOnClickListener {
                                val directions = HomeFragmentDirections.actionHomeFragmentToDetailFragment(countryCase[0], updatedAt)
                                findNavController().navigate(directions)
                            }
                        }
                    } else {
                        includedRoot.gone()
                    }
                }
                is Outcome.Failure -> {
                    activity!!.toast(activity!!.getString(R.string.error_loading))
                }
            }
        })
    }

    private fun fillListUI(countriesStat: List<CountriesStat>) {

        statsAdapter?.submitList(countriesStat)

    }

    private fun initRecyclerView(){

        statsAdapter = StatsAdapter(this)
//        val linearLayoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        statsRecycler.apply {
//            layoutManager = linearLayoutManager
            adapter = statsAdapter
            addItemDecoration(
                DividerItemDecoration(
                    activity,
                    DividerItemDecoration.VERTICAL
                )
            )

        }
    }

    private fun getLocationPermission() {
        Dexter.withActivity(activity)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                @SuppressLint("MissingPermission")
                override fun onPermissionGranted(response: PermissionGrantedResponse) {/* ... */
                    // Acquire a reference to the system Location Manager
                    locationManager =
                        activity!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                    Log.d(TAG, "onPermissionGranted: Location permission granted")
                    // Define a listener that responds to location updates
                    locationListener = object : LocationListener {

                        override fun onLocationChanged(location: Location) {
                            Log.d("Location", "onLocationChanged: ")
                            // Called when a new location is found by the network location provider.
                            makeUseOfNewLocation(location)
                        }

                        override fun onStatusChanged(
                            provider: String,
                            status: Int,
                            extras: Bundle
                        ) {
                            Log.d("Location", "onStatusChanged: ")
                        }

                        override fun onProviderEnabled(provider: String) {
                            Log.d("Location", "onProviderEnabled: ")
                        }

                        override fun onProviderDisabled(provider: String) {
                            Log.d("Location", "onProviderDisabled: $provider")
                        }
                    }

                    // Register the listener with the Location Manager to receive location updates
                    locationManager!!.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0,
                        0f,
                        locationListener
                    )
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {/* ... */
                    activity!!.toast(response.permissionName + " denied")
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest,
                    token: PermissionToken
                ) {/* ... */
                }
            }).check()
    }

    private fun makeUseOfNewLocation(location: Location) {
        Log.d(
            TAG, "makeUseOfNewLocation: "
                    + "Location: latitude-> ${location.latitude} & longitude-> ${location.longitude}"
        )
        try {
            val cache = Cache(activity!!)
            val geocoder = Geocoder(activity, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            Log.d(TAG, "makeUseOfNewLocation: Addresses : $addresses")
            Log.d(TAG, "makeUseOfNewLocation: Addresses : ${addresses[0].countryName}")
            if (addresses[0].maxAddressLineIndex >= 0) {
                //locationEdit.setText(addresses[0].getAddressLine(0))
                countryName = addresses[0].countryName
                cache.saveData(Cache.COUNTRY_NAME, countryName!!)
            } else {
                //locationEdit.setText(addresses[0].locality)
                try {
                    countryName = addresses[0].countryName
                    cache.saveData(Cache.COUNTRY_NAME, countryName!!)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            removeLocationListener()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        removeLocationListener()
    }

    private fun removeLocationListener() {
        locationManager?.removeUpdates(locationListener)
        locationManager = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {

//        val menuInflater = menuInflater
        inflater.inflate(R.menu.search_menu, menu)

        super.onCreateOptionsMenu(menu, inflater)

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when(item.itemId){

            R.id.app_bar_search -> openSearchFragment()

        }

        return super.onOptionsItemSelected(item)
    }

    private fun openSearchFragment() {
        findNavController().navigate(R.id.action_homeFragment_to_viewAllStatsFragment)
//        HomeFragmentDirections.actionHomeFragmentToViewAllStatsFragment()

    }

    override fun onItemClick(statsObject: CountriesStat) {

        Log.d("statsadapter", "Home Fragment Click to hua hai")


        val directions = HomeFragmentDirections.actionHomeFragmentToDetailFragment(statsObject, updatedAt)
        findNavController().navigate(directions)

    }

}
