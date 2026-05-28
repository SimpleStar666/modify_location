package com.mocklocation.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.mocklocation.app.R
import com.mocklocation.app.databinding.FragmentMapBinding
import com.mocklocation.app.util.GeoCoder
import com.mocklocation.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val geoCoder = GeoCoder()

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
        map?.setUseDataConnection(true)

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

        viewModel.onLocationSelected(
            geoPoint.latitude, geoPoint.longitude,
            "${geoPoint.latitude}, ${geoPoint.longitude}", ""
        )

        reverseGeocode(geoPoint.latitude, geoPoint.longitude)
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        lifecycleScope.launch {
            val result = geoCoder.reverseGeocode(lat, lng)
            if (result != null) {
                viewModel.onLocationSelected(lat, lng, result.name, result.address)
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
        if (query.isBlank()) return

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

        lifecycleScope.launch {
            try {
                val result = geoCoder.search(query)
                if (result != null) {
                    withContext(Dispatchers.Main) {
                        val geoPoint = GeoPoint(result.latitude, result.longitude)
                        map?.controller?.animateTo(geoPoint)
                        selectLocation(geoPoint)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "未找到该地点，请尝试输入坐标（如：22.5431, 114.0579）",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "搜索失败，请尝试输入坐标（如：22.5431, 114.0579）",
                        Toast.LENGTH_LONG
                    ).show()
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
                state.error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun checkPermissions() {
        val perms = mutableListOf<String>()
        if (!PermissionHelper.hasLocationPermission(requireContext())) {
            perms.addAll(PermissionHelper.locationPermissions)
        }
        if (Build.VERSION.SDK_INT < 29) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), perms.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            map?.invalidate()
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
        private const val REQUEST_PERMISSIONS = 100
    }
}
