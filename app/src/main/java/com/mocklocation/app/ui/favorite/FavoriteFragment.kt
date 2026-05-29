package com.mocklocation.app.ui.favorite

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mocklocation.app.R
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.databinding.FragmentFavoriteBinding
import com.mocklocation.app.databinding.ItemFavoriteBinding
import com.mocklocation.app.util.PermissionHelper
import kotlinx.coroutines.launch

class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FavoriteViewModel by viewModels()
    private val adapter = FavoriteAdapter()
    private var pendingMockLocation: FavoriteLocation? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = adapter

        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favorites.collect {
                adapter.submitList(it)
            }
        }
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_favorite, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etAddress = dialogView.findViewById<EditText>(R.id.et_address)
        val etLat = dialogView.findViewById<EditText>(R.id.et_latitude)
        val etLng = dialogView.findViewById<EditText>(R.id.et_longitude)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_add_favorite)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = etName.text.toString()
                val address = etAddress.text.toString()
                val lat = etLat.text.toString().toDoubleOrNull() ?: 0.0
                val lng = etLng.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isNotBlank() && lat != 0.0) {
                    viewModel.addFavorite(name, address, lat, lng)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "请填写完整信息",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(location: FavoriteLocation) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_favorite, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_name)
        val etAddress = dialogView.findViewById<EditText>(R.id.et_address)
        val etLat = dialogView.findViewById<EditText>(R.id.et_latitude)
        val etLng = dialogView.findViewById<EditText>(R.id.et_longitude)

        etName.setText(location.name)
        etAddress.setText(location.address)
        etLat.setText(location.latitude.toString())
        etLng.setText(location.longitude.toString())

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_edit_favorite)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val updated = location.copy(
                    name = etName.text.toString(),
                    address = etAddress.text.toString(),
                    latitude = etLat.text.toString().toDoubleOrNull() ?: location.latitude,
                    longitude = etLng.text.toString().toDoubleOrNull() ?: location.longitude
                )
                viewModel.updateFavorite(updated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MOCK_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingMockLocation?.let { item ->
                    viewModel.startMocking(item)
                    Toast.makeText(requireContext(), "正在模拟定位: ${item.name}", Toast.LENGTH_SHORT).show()
                    try {
                        findNavController().navigate(R.id.mapFragment)
                    } catch (_: Exception) {
                    }
                }
            } else {
                Toast.makeText(requireContext(), "需要位置权限才能使用模拟定位功能", Toast.LENGTH_LONG).show()
            }
            pendingMockLocation = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_MOCK_PERMISSION = 102
    }

    inner class FavoriteAdapter : RecyclerView.Adapter<FavoriteAdapter.ViewHolder>() {

        private var items: List<FavoriteLocation> = emptyList()

        fun submitList(list: List<FavoriteLocation>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFavoriteBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvName.text = item.name
            holder.binding.tvAddress.text = item.address
            holder.binding.btnLocate.setOnClickListener {
                if (!PermissionHelper.hasLocationPermission(requireContext())) {
                    pendingMockLocation = item
                    PermissionHelper.requestLocationPermission(requireActivity(), REQUEST_MOCK_PERMISSION)
                } else {
                    viewModel.startMocking(item)
                    Toast.makeText(requireContext(), "正在模拟定位: ${item.name}", Toast.LENGTH_SHORT).show()
                    try {
                        findNavController().navigate(R.id.mapFragment)
                    } catch (_: Exception) {
                    }
                }
            }
            holder.binding.root.setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setItems(arrayOf("编辑", "删除")) { _, which ->
                        when (which) {
                            0 -> showEditDialog(item)
                            1 -> viewModel.deleteFavorite(item)
                        }
                    }
                    .show()
                true
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemFavoriteBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
