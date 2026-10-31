package com.tvlock.app

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var locked: Boolean
)

/** One row per installed app. Pressing OK on a row turns its lock on or off. */
class AppAdapter(
    private val items: List<AppEntry>,
    private val onToggled: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
        val state: TextView = view.findViewById(R.id.appState)
        val toggle: Switch = view.findViewById(R.id.appSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.icon.setImageDrawable(entry.icon)
        holder.label.text = entry.label
        showState(holder, entry)
        holder.itemView.setOnClickListener {
            entry.locked = !entry.locked
            onToggled(entry)
            // Update this row in place so the remote's focus does not jump.
            showState(holder, entry)
        }
    }

    private fun showState(holder: Holder, entry: AppEntry) {
        val context = holder.itemView.context
        holder.toggle.isChecked = entry.locked
        holder.state.setText(if (entry.locked) R.string.state_locked else R.string.state_open)
        holder.state.setTextColor(
            context.getColor(if (entry.locked) R.color.accent else R.color.text_secondary)
        )
    }
}
