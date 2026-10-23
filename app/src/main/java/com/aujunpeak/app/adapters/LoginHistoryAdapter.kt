package com.aujunpeak.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aujunpeak.app.R
import com.aujunpeak.app.utils.LoginHistory

class LoginHistoryAdapter(private val items: List<LoginHistory.Entry>) :
    RecyclerView.Adapter<LoginHistoryAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView    = view.findViewById(R.id.ivHistoryIcon)
        val tvDevice: TextView   = view.findViewById(R.id.tvHistoryDevice)
        val tvTime: TextView     = view.findViewById(R.id.tvHistoryTime)
        val tvIp: TextView       = view.findViewById(R.id.tvHistoryIp)
        val tvCountry: TextView  = view.findViewById(R.id.tvHistoryCountry)
        val tvIndex: TextView    = view.findViewById(R.id.tvHistoryIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_login_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvDevice.text  = item.deviceName.ifEmpty { "Thiết bị không xác định" }
        holder.tvTime.text    = item.timeFormatted
        holder.tvIp.text      = item.ip.ifEmpty { "N/A" }
        holder.tvCountry.text = item.country.ifEmpty { "N/A" }
        holder.tvIndex.text   = "#${items.size - position}"
    }

    override fun getItemCount(): Int = items.size
}
