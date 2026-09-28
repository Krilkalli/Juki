package com.nmichail.android_pmu.presentation.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.juki.R
import com.example.juki.Author

class AuthorsAdapter(
        context: Context,
        private val authors: List<Author>
) : ArrayAdapter<Author>(context, 0, authors) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_author, parent, false)

        val author = authors[position]
        view.findViewById<ImageView>(R.id.ivAuthorPhoto).setImageResource(author.photoResId)
        view.findViewById<TextView>(R.id.tvAuthorName).text = author.name

        return view
    }
}