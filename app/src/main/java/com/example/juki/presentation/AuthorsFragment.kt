package com.nmichail.android_pmu.presentation.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.example.juki.R
import com.example.juki.Author

class AuthorsFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_authors, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authors = listOf(
                Author(getString(R.string.author_korotkov), R.drawable.KirillK),
                Author(getString(R.string.author_gorbachev), R.drawable.KirillG)
        )

        val listView = view.findViewById<ListView>(R.id.lvAuthors)
        listView.adapter = AuthorsAdapter(requireContext(), authors)
    }
}