package com.mocklocation.app.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeResult
import com.mocklocation.app.R
import com.mocklocation.app.databinding.FragmentMapBinding
import com.mocklocation.app.util.PermissionHelper
import kotlinx.coroutines.launch

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private var aMap: AMap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMap(savedInstanceState)
        initSearch()
        initMockButton()
        observeState()
        checkPermissions()
    }

    private fun initMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        aMap = binding.mapView.map
        aMap?.setOnMapClickListener { latLng ->
            selectLocation(latLng)
        }
    }

    private fun selectLocation(latLng: LatLng) {
        aMap?.clear()
        aMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("${latLng.latitude}, ${latLng.longitude}")
        )
        aMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))

        val geocodeSearch = GeocodeSearch(requireContext())
        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                val address = result?.regeocodeAddress?.formatAddress ?: ""
                val name = result?.regeocodeAddress?.poiResults?.firstOrNull()?.poiName
                    ?: address
                viewModel.onLocationSelected(
                    latLng.latitude, latLng.longitude, name, address
                )
            }
            override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {}
        })

        val query = GeocodeSearch.RegeocodeQuery(
            com.amap.api.services.core.LatLonPoint(latLng.latitude, latLng.longitude),
            200f,
            GeocodeSearch.AMAP
        )
        geocodeSearch.getFromLocationAsyn(query)
    }

    private fun initSearch() {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.searchInput.text.toString())
                true
            } else false
        }
    }

    private fun performSearch(query: String) {
        val coordRegex = Regex("""^(-?\d+\.?\d*)\s*[,，\s]\s*(-?\d+\.?\d*)$""")
        val match = coordRegex.find(query)
        if (match != null) {
            val lat = match.groupValues[1].toDouble()
            val lng = match.groupValues[2].toDouble()
            selectLocation(LatLng(lat, lng))
            return
        }

        val geocodeSearch = GeocodeSearch(requireContext())
        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                val first = result?.geocodeAddressList?.firstOrNull()
                if (first != null) {
                    val latLng = LatLng(first.latLonPoint.latitude, first.latLonPoint.longitude)
                    selectLocation(latLng)
                } else {
                    Toast.makeText(requireContext(), R.string.no_results, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {}
        })

        val geocodeQuery = GeocodeSearch.GeocodeQuery(query, "")
        geocodeSearch.getFromLocationNameAsyn(geocodeQuery)
    }

    private fun initMockButton() {
        binding.btnMock.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.selectedLat == 0.0 && state.selectedLng == 0.0) {
                Toast.makeText(requireContext(), "请先选择一个位置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (state.isMocking) {
                viewModel.stopMocking()
            } else {
                viewModel.startMocking()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.tvLocationName.text = if (state.selectedName.isNotEmpty()) {
                    "📍 ${state.selectedName}"
                } else {
                    "点击地图选择位置"
                }
                binding.tvLocationCoords.text = if (state.selectedLat != 0.0) {
                    "${state.selectedLat}°N, ${state.selectedLng}°E"
                } else {
                    ""
                }
                if (state.isMocking) {
                    binding.btnMock.text = getString(R.string.btn_stop_mock)
                } else {
                    binding.btnMock.text = getString(R.string.btn_start_mock)
                }
            }
        }
    }

    private fun checkPermissions() {
        if (!PermissionHelper.hasLocationPermission(requireContext())) {
            PermissionHelper.requestLocationPermission(
                requireActivity(),
                REQUEST_LOCATION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    companion object {
        private const val REQUEST_LOCATION = 100
    }
}
