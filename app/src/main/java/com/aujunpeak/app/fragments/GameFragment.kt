package com.aujunpeak.app.fragments

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.aujunpeak.app.activities.GameBoostActivity
import com.aujunpeak.app.adapters.AppAdapter
import com.aujunpeak.app.databinding.FragmentGameBinding

class GameFragment : Fragment() {

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    private var allApps: List<ApplicationInfo> = emptyList()
    private lateinit var adapter: AppAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pm = requireContext().packageManager
        allApps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString() }

        adapter = AppAdapter(allApps, pm) { appInfo ->
            val name = appInfo.loadLabel(pm).toString()
            val intent = Intent(requireContext(), GameBoostActivity::class.java).apply {
                putExtra("app_name", name)
                putExtra("package_name", appInfo.packageName)
            }
            startActivity(intent)
        }

        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = adapter
        binding.rvApps.isNestedScrollingEnabled = false
        binding.rvApps.setHasFixedSize(false)
        binding.tvAppCount.text = allApps.size.toString()

        setupSearch(pm)

        // Animate list in
        binding.rvApps.alpha = 0f
        binding.rvApps.animate().alpha(1f).setDuration(400).setStartDelay(100).start()
    }

    private fun setupSearch(pm: android.content.pm.PackageManager) {
        binding.etSearchGame.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                filterApps(query, pm)
            }
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchGame.setText("")
        }
    }

    private fun filterApps(query: String, pm: android.content.pm.PackageManager) {
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.loadLabel(pm).toString().contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        binding.tvAppCount.text = filtered.size.toString()
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        (activity as? com.aujunpeak.app.activities.MainActivity)?.checkKeyOrForceLogout()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
