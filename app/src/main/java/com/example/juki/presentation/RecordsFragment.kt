package com.example.juki.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.juki.MyApplication
import com.example.juki.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class RecordsFragment : Fragment() {
    private val scoreRepository get() = (requireActivity().application as MyApplication).scoreRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_records, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = view.findViewById<ListView>(R.id.recordsList)
        val status = view.findViewById<TextView>(R.id.recordsStatus)
        status.setText(R.string.records_loading)
        status.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    scoreRepository.observeRecords().collect { records ->
                        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        list.adapter = ArrayAdapter(
                            requireContext(), android.R.layout.simple_list_item_1,
                            records.map { record ->
                                getString(
                                    R.string.record_entry,
                                    record.playerName.trim(), record.score,
                                    record.difficulty, dateFormat.format(Date(record.createdAt))
                                )
                            }
                        )
                        status.setText(R.string.records_empty)
                        status.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.setText(R.string.records_load_error)
            }
        }
    }
}
