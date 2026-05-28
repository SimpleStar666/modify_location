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
import com.mocklocation.app.R
import com.mocklocation.app.databinding.FragmentMapBinding
import com.mocklocation.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private var map: MapView? = null
    private var currentMarker: Marker? = null
    private val httpClient = OkHttpClient()
    private val gson = Gson()

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
        initMap()
        initSearch()
        initMockButton()
        observeState()
        checkPermissions()
    }

    private fun initMap() {
        map = binding.mapView
        map?.setTileSource(TileSourceFactory.MAPNIK)
        map?.setMultiTouchControls(true)
        map?.controller?.setZoom(15.0)
        map?.controller?.setCenter(GeoPoint(39.9042, 116.4074))

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(geoPoint: GeoPoint): Boolean {
                selectLocation(geoPoint)
                return true
            }
            override fun longPressHelper(geoPoint: GeoPoint): Boolean {
                return false
            }
        }
        map?.overlays?.add(MapEventsOverlay(mapEventsReceiver))
    }

    private fun selectLocation(geoPoint: GeoPoint) {
        currentMarker?.let { map?.overlays?.remove(it) }

        val marker = Marker(map)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "${geoPoint.latitude}, ${geoPoint.longitude}"
        map?.overlays?.add(marker)
        currentMarker = marker
        map?.invalidate()

        reverseGeocode(geoPoint.latitude, geoPoint.longitude)
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=18&addressdetails=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MockLocationApp/1.0")
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@launch

                val json = gson.fromJson(body, JsonObject::class.java)
                val displayName = json.get("display_name")?.asString ?: ""
                val name = json.get("name")?.asString
                    ?: json.getAsJsonObject("address")?.get("road")?.asString
                    ?: displayName

                withContext(Dispatchers.Main) {
                    viewModel.onLocationSelected(lat, lng, name, displayName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    viewModel.onLocationSelected(lat, lng, "$lat, $lng", "")
                }
            }
        }
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
            val geoPoint = GeoPoint(lat, lng)
            map?.controller?.animateTo(geoPoint)
            selectLocation(geoPoint)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MockLocationApp/1.0")
                    .build()
                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: return@launch

                val results = gson.fromJson(body, com.google.gson.JsonArray::class.java)
                if (results.size() > 0) {
                    val first = results[0].asJsonObject
                    val lat = first.get("lat").asString.toDouble()
                    val lng = first.get("lon").asString.toDouble()

                    withContext(Dispatchers.Main) {
                        val geoPoint = GeoPoint(lat, lng)
                        map?.controller?.animateTo(geoPoint)
                        selectLocation(geoPoint)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), R.string.no_results, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.no_results, Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        binding.mapView.onDetach()
        _binding = null
    }

    companion object {
        private const val REQUEST_LOCATION = 100
    }
}
