package com.aujunpeak.app.adapters

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aujunpeak.app.R

class AppAdapter(
    private var apps: List<ApplicationInfo>,
    private val pm: PackageManager,
    private val onClick: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val pkg: TextView = view.findViewById(R.id.tvPackageName)
    }

    fun submitList(newApps: List<ApplicationInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.loadIcon(pm))
        holder.name.text = app.loadLabel(pm).toString()
        holder.pkg.text = app.packageName
        holder.itemView.setOnClickListener {
            // Ripple click animation
            holder.itemView.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                holder.itemView.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction {
                    onClick(app)
                }.start()
            }.start()
        }

        // Staggered entrance animation
        holder.itemView.alpha = 0f
        holder.itemView.translationX = -30f
        holder.itemView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(300)
            .setStartDelay((position * 40L).coerceAtMost(600L))
            .start()
    }

    override fun getItemCount(): Int = apps.size
}
