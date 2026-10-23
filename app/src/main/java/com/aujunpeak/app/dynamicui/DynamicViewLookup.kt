package com.aujunpeak.app.dynamicui

import android.view.View
import android.view.ViewGroup
import com.aujunpeak.app.R

object DynamicViewLookup {
    fun find(root: View, name: String): View? {
        if (name == "__root__") return root
        return findRecursive(root, name)
    }

    private fun findRecursive(view: View, name: String): View? {
        val dynamicName = view.getTag(R.id.dynamic_ui_component_id) as? String
        if (dynamicName == name) return view
        if (view.id != View.NO_ID) {
            val resourceName = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            if (resourceName == name) return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findRecursive(view.getChildAt(i), name)
                if (found != null) return found
            }
        }
        return null
    }

    fun walk(root: View, block: (View) -> Unit) {
        block(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) walk(root.getChildAt(i), block)
        }
    }
}
