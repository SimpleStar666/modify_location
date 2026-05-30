package com.mocklocation.app.ui.map

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.mocklocation.app.R
import com.mocklocation.app.databinding.FragmentMapBinding
import com.mocklocation.app.util.AmapTileSource
import com.mocklocation.app.util.CoordTransform
import com.mocklocation.app.util.GeoCoder
import com.mocklocation.app.util.MockDiagnostic
import com.mocklocation.app.util.PermissionHelper
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()
    private var map: MapView? = null
    private var currentMarker: Marker? = null
    private lateinit var geoCoder: GeoCoder
    private var pendingMockStart = false

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
        geoCoder = GeoCoder(requireContext().applicationContext)
        initMap()
        initSearch()
        initButtons()
        observeState()
        checkPermissions()
    }

    private fun initMap() {
        map = binding.mapView
        map?.setTileSource(AmapTileSource())
        map?.setMultiTouchControls(true)
        map?.isTilesScaledToDpi = true
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

        val gcjLat = geoPoint.latitude
        val gcjLng = geoPoint.longitude
        val wgs84 = CoordTransform.gcj02ToWgs84(gcjLat, gcjLng)

        viewModel.onLocationSelected(
            wgs84[0], wgs84[1],
            gcjLat, gcjLng,
            "${gcjLat}, ${gcjLng}", ""
        )

        reverseGeocode(gcjLat, gcjLng)
    }

    private fun reverseGeocode(lat: Double, lng: Double) {
        lifecycleScope.launch {
            val searchResult = geoCoder.reverseGeocode(lat, lng)
            val geoResult = searchResult.result
            if (geoResult != null) {
                val wgs84 = CoordTransform.gcj02ToWgs84(geoResult.latitude, geoResult.longitude)
                viewModel.onLocationSelected(
                    wgs84[0], wgs84[1],
                    geoResult.latitude, geoResult.longitude,
                    geoResult.name, geoResult.address
                )
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
            val gcj02 = CoordTransform.wgs84ToGcj02(lat, lng)
            val geoPoint = GeoPoint(gcj02[0], gcj02[1])
            map?.controller?.animateTo(geoPoint)
            selectLocation(geoPoint)
            return
        }

        val key = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("amap_api_key", null)
        if (key.isNullOrBlank()) {
            showApiKeyDialog()
            return
        }

        lifecycleScope.launch {
            val searchResult = geoCoder.search(query)
            if (searchResult.result != null) {
                val geoResult = searchResult.result
                val geoPoint = GeoPoint(geoResult.latitude, geoResult.longitude)
                map?.controller?.animateTo(geoPoint)
                selectLocation(geoPoint)
            } else {
                Toast.makeText(
                    requireContext(),
                    searchResult.error ?: "搜索失败",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showApiKeyDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "输入高德 Web 服务 API Key"
            setPadding(48, 24, 48, 24)
            val savedKey = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("amap_api_key", "")
            setText(savedKey)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("设置高德 API Key")
            .setMessage("搜索地名需要高德 Web 服务 API Key（免费）。\n\n" +
                "获取方式：\n" +
                "1. 访问 https://lbs.amap.com\n" +
                "2. 注册免费账号\n" +
                "3. 进入控制台 → 我的应用 → 添加 Key\n" +
                "4. 服务平台选「Web服务」\n\n" +
                "免费额度：每天 5000 次")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val key = editText.text.toString().trim()
                if (key.isNotBlank()) {
                    requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("amap_api_key", key)
                        .apply()
                    Toast.makeText(requireContext(), "API Key 已保存，请重新搜索", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun initButtons() {
        binding.btnMock.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.selectedLat == 0.0 && state.selectedLng == 0.0) {
                Toast.makeText(requireContext(), "请先选择一个位置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (state.isMocking) {
                viewModel.stopMocking()
            } else {
                startMockWithPreCheck()
            }
        }

        binding.btnFavorite.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.selectedLat == 0.0 && state.selectedLng == 0.0) {
                Toast.makeText(requireContext(), "请先选择一个位置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addFavorite()
        }

        binding.btnWzryGuide.setOnClickListener {
            showWzryGuide()
        }
    }

    private fun startMockWithPreCheck() {
        if (!PermissionHelper.hasLocationPermission(requireContext())) {
            pendingMockStart = true
            PermissionHelper.requestLocationPermission(requireActivity(), REQUEST_MOCK_PERMISSION)
            return
        }

        val hasFineOnly = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineOnly) {
            showNeedFineLocationDialog()
            return
        }

        val diagnostic = viewModel.runDiagnostic()
        if (diagnostic.isReady) {
            viewModel.startMocking()
            showCompatibilityInfoIfNeeded()
        } else {
            showDiagnosticDialog(diagnostic)
        }
    }

    private fun showNeedFineLocationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("需要精确位置权限")
            .setMessage("模拟定位需要「精确位置」权限，不能只有「大致位置」。\n\n" +
                "请按以下步骤操作：\n" +
                "1. 长按本应用图标\n" +
                "2. 点击「应用信息」\n" +
                "3. 点击「权限」\n" +
                "4. 点击「位置信息」\n" +
                "5. 选择「仅在使用中允许」或「始终允许」\n" +
                "6. 确保打开了「使用精确位置」开关")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDiagnosticDialog(diagnostic: MockDiagnostic) {
        val message = buildString {
            append("━━━ 诊断结果 ━━━\n\n")
            append(diagnostic.toDisplayText())
            append("\n━━━ 解决方法 ━━━\n\n")
            append(diagnostic.getSolutionText())
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("模拟定位预检失败")
            .setMessage(message)
            .setNegativeButton("关闭", null)

        if (!diagnostic.hasFineLocation) {
            builder.setPositiveButton("去应用设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        } else if (!diagnostic.canAddTestProvider) {
            builder.setPositiveButton("去开发者选项") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }

        builder.show()
    }

    private fun showCompatibilityInfoIfNeeded() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("compatibility_shown", false)) return
        prefs.edit().putBoolean("compatibility_shown", true).apply()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.compatibility_title)
            .setMessage(R.string.compatibility_message)
            .setNeutralButton(R.string.wzry_guide_button) { _, _ ->
                showWzryGuide()
            }
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showWzryGuide() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wzry_guide_title)
            .setMessage(R.string.wzry_guide_message)
            .setPositiveButton("知道了", null)
            .show()
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
                    binding.tvMockStatus.visibility = View.VISIBLE

                    val gpsStatus = if (state.gpsMocked) "✅GPS" else "❌GPS${state.gpsError?.let { "($it)" } ?: ""}"
                    val netStatus = if (state.networkMocked) "✅网络" else "❌网络${state.networkError?.let { "($it)" } ?: ""}"

                    val gpsMatch = state.gpsMocked && state.verifyGpsLat != 0.0 &&
                        Math.abs(state.verifyGpsLat - state.selectedLat) < 0.001 &&
                        Math.abs(state.verifyGpsLng - state.selectedLng) < 0.001
                    val netMatch = state.networkMocked && state.verifyNetworkLat != 0.0 &&
                        Math.abs(state.verifyNetworkLat - state.selectedLat) < 0.001 &&
                        Math.abs(state.verifyNetworkLng - state.selectedLng) < 0.001

                    val line1 = "$gpsStatus  $netStatus"
                    val line2 = when {
                        gpsMatch || netMatch -> "✅ 系统定位已切换到模拟位置"
                        state.verifyGpsLat != 0.0 || state.verifyNetworkLat != 0.0 -> "⚠️ 系统定位与模拟位置不一致"
                        !state.gpsMocked && !state.networkMocked -> {
                            val err = state.gpsError ?: state.networkError ?: ""
                            when {
                                err.contains("位置权限") -> "⚠️ 请授予「精确位置」权限后重试"
                                err.contains("未选为模拟") -> "⚠️ 请在开发者选项中重新选择本应用"
                                else -> "⏳ 等待系统读取模拟位置..."
                            }
                        }
                        else -> "⏳ 等待系统读取模拟位置..."
                    }

                    val rawInfo = buildString {
                        val rawGps = state.gpsRawError
                        val rawNet = state.networkRawError
                        if (!rawGps.isNullOrBlank() || !rawNet.isNullOrBlank()) {
                            append("\n")
                            if (!rawGps.isNullOrBlank()) append("系统错误: $rawGps")
                            else if (!rawNet.isNullOrBlank()) append("系统错误: $rawNet")
                        }
                    }

                    binding.tvMockStatus.text = "$line1\n$line2$rawInfo"
                    if (gpsMatch || netMatch) {
                        binding.tvMockStatus.setTextColor(0xFF4CAF50.toInt())
                    } else if (!state.gpsMocked && !state.networkMocked) {
                        binding.tvMockStatus.setTextColor(0xFFF44336.toInt())
                    } else {
                        binding.tvMockStatus.setTextColor(0xFFFF9800.toInt())
                    }
                } else {
                    binding.btnMock.text = getString(R.string.btn_start_mock)
                    binding.tvMockStatus.visibility = View.GONE
                }
                state.error?.let {
                    AlertDialog.Builder(requireContext())
                        .setTitle("模拟定位")
                        .setMessage(it)
                        .setPositiveButton("确定", null)
                        .show()
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
        when (requestCode) {
            REQUEST_MOCK_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (pendingMockStart) {
                        pendingMockStart = false
                        startMockWithPreCheck()
                    }
                } else {
                    pendingMockStart = false
                    Toast.makeText(requireContext(), "需要位置权限才能使用模拟定位功能", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_PERMISSIONS -> {
                map?.invalidate()
            }
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
        private const val REQUEST_MOCK_PERMISSION = 101
    }
}
