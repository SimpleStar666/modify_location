package com.mocklocation.app.ui.history

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mocklocation.app.R
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.databinding.FragmentHistoryBinding
import com.mocklocation.app.databinding.ItemHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private val adapter = HistoryAdapter()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.currentList[vh.adapterPosition]
                viewModel.delete(item)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvHistory)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_clear_all) {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.confirm_clear_history)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.clearAll()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            } else false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.history.collect {
                adapter.submitList(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        private var items: List<LocationHistory> = emptyList()
        val currentList get() = items

        fun submitList(list: List<LocationHistory>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvName.text = item.name
            holder.binding.tvTime.text = dateFormat.format(Date(item.usedAt))
            holder.binding.btnReuse.setOnClickListener {
                viewModel.startMocking(item)
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemHistoryBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
