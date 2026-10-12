package com.example.juki.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.juki.MainActivity
import com.example.juki.MyApplication
import com.example.juki.R
import com.example.juki.domain.model.Player
import com.example.juki.domain.model.User
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistrationFragment : Fragment() {

    private val userRepository get() = (requireActivity().application as MyApplication).userRepository

    private var users: List<User> = emptyList()
    private var updatingSpinner = false
    private var loadingPlayers = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.registration_form, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = view.findViewById<EditText>(R.id.name)
        val surname = view.findViewById<EditText>(R.id.surname)
        val otchestvo = view.findViewById<EditText>(R.id.othcestvo)
        val genderGroup = view.findViewById<RadioGroup>(R.id.myRadioGroup)
        val courseSpinner = view.findViewById<Spinner>(R.id.combobox)
        val seekBar = view.findViewById<SeekBar>(R.id.seekBar)
        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        val buttonShowZadiak = view.findViewById<Button>(R.id.showZnak)
        val buttonRegister = view.findViewById<Button>(R.id.btnRegister)

        val tvResult = view.findViewById<TextView>(R.id.tvResult)
        val ivZodiac = view.findViewById<ImageView>(R.id.ivZodiac)
        val spinnerPlayers = view.findViewById<Spinner>(R.id.spinnerPlayers)
        val tvCurrentPlayer = view.findViewById<TextView>(R.id.tvCurrentPlayer)

        spinnerPlayers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (updatingSpinner) return
                if (position == 0) {
                    if ((activity as? MainActivity)?.currentUser == null) return
                    (activity as? MainActivity)?.currentUser = null
                    updateCurrentPlayerLabel(tvCurrentPlayer, null)
                    name.text.clear()
                    surname.text.clear()
                    otchestvo.text.clear()
                    genderGroup.clearCheck()
                    courseSpinner.setSelection(0)
                    seekBar.progress = 20
                    tvResult.text = ""
                    ivZodiac.visibility = View.GONE
                    val date = Calendar.getInstance()
                    calendarView.date = date.timeInMillis
                    return
                }
                val user = users.getOrNull(position - 1) ?: return
                val activity = activity as? MainActivity ?: return

                activity.currentUser = user
                updateCurrentPlayerLabel(tvCurrentPlayer, user)
                fillForm(
                    user = user,
                    name = name,
                    surname = surname,
                    otchestvo = otchestvo,
                    genderGroup = genderGroup,
                    courseSpinner = courseSpinner,
                    seekBar = seekBar
                )
                val date = Calendar.getInstance().apply {
                    set(user.birthYear, user.birthMonth - 1, user.birthDay)
                }
                calendarView.date = date.timeInMillis
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        buttonShowZadiak.setOnClickListener {
            val player = buildPlayer(
                name = name,
                surname = surname,
                otchestvo = otchestvo,
                genderGroup = genderGroup,
                courseSpinner = courseSpinner,
                seekBar = seekBar,
                calendarView = calendarView
            )
            showPlayerPreview(player, tvResult, ivZodiac)
        }

        buttonRegister.setOnClickListener {
            val player = buildPlayer(
                name = name,
                surname = surname,
                otchestvo = otchestvo,
                genderGroup = genderGroup,
                courseSpinner = courseSpinner,
                seekBar = seekBar,
                calendarView = calendarView
            )

            if (player.name.isBlank() || player.surname.isBlank()) {
                Toast.makeText(requireContext(), R.string.fill_name_surname, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val activity = activity as? MainActivity ?: return@setOnClickListener

            buttonRegister.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        userRepository.register(player)
                    }
                    activity.currentUser = saved
                    showPlayerPreview(player, tvResult, ivZodiac)
                    updateCurrentPlayerLabel(tvCurrentPlayer, saved)
                    loadPlayers(spinnerPlayers, tvCurrentPlayer)
                    Toast.makeText(requireContext(), R.string.player_registered, Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.player_register_error, Toast.LENGTH_LONG).show()
                } finally {
                    buttonRegister.isEnabled = !loadingPlayers
                }
            }
        }

        updateCurrentPlayerLabel(tvCurrentPlayer, (activity as? MainActivity)?.currentUser)
    }

    override fun onResume() {
        super.onResume()
        val spinnerPlayers = view?.findViewById<Spinner>(R.id.spinnerPlayers) ?: return
        val tvCurrentPlayer = view?.findViewById<TextView>(R.id.tvCurrentPlayer) ?: return
        updateCurrentPlayerLabel(tvCurrentPlayer, (activity as? MainActivity)?.currentUser)
        loadPlayers(spinnerPlayers, tvCurrentPlayer)
    }

    private fun loadPlayers(spinner: Spinner, tvCurrentPlayer: TextView) {
        val activity = activity as? MainActivity ?: return
        if (loadingPlayers) return
        loadingPlayers = true
        val registerButton = requireView().findViewById<Button>(R.id.btnRegister)
        registerButton.isEnabled = false
        spinner.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                users = withContext(Dispatchers.IO) { userRepository.getAll() }
                val labels = listOf(getString(R.string.select_player_hint)) +
                    users.map { formatPlayerName(it) }
                updatingSpinner = true
                spinner.adapter = ArrayAdapter(
                    requireContext(), android.R.layout.simple_spinner_dropdown_item, labels
                )
                val selectedIndex = users.indexOfFirst { it.id == activity.currentUser?.id }
                spinner.setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0, false)
                updatingSpinner = false
                updateCurrentPlayerLabel(tvCurrentPlayer, activity.currentUser)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.players_load_error, Toast.LENGTH_LONG).show()
            } finally {
                loadingPlayers = false
                updatingSpinner = false
                spinner.isEnabled = true
                registerButton.isEnabled = true
            }
        }
    }

    private fun updateCurrentPlayerLabel(label: TextView, user: User?) {
        label.text = if (user == null) {
            getString(R.string.current_player_none)
        } else {
            getString(R.string.current_player, formatPlayerName(user))
        }
    }

    private fun formatPlayerName(user: User): String {
        return listOf(user.surname, user.name, user.otchestvo)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun fillForm(
        user: User,
        name: EditText,
        surname: EditText,
        otchestvo: EditText,
        genderGroup: RadioGroup,
        courseSpinner: Spinner,
        seekBar: SeekBar
    ) {
        name.setText(user.name)
        surname.setText(user.surname)
        otchestvo.setText(user.otchestvo)
        when (user.gender) {
            "Мужчина" -> genderGroup.check(R.id.male)
            "Женщина" -> genderGroup.check(R.id.female)
            else -> genderGroup.clearCheck()
        }
        courseSpinner.setSelection((user.course - 1).coerceIn(0, 3))
        seekBar.progress = user.difficulty
    }

    private fun buildPlayer(
        name: EditText,
        surname: EditText,
        otchestvo: EditText,
        genderGroup: RadioGroup,
        courseSpinner: Spinner,
        seekBar: SeekBar,
        calendarView: CalendarView
    ): Player {
        val date = Calendar.getInstance().apply { timeInMillis = calendarView.date }
        val day = date.get(Calendar.DAY_OF_MONTH)
        val month = date.get(Calendar.MONTH) + 1
        val birthYear = date.get(Calendar.YEAR)
        val zodiac = getZodiac(day, month)
        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.male   -> "Мужчина"
            R.id.female -> "Женщина"
            else        -> "Вы не выбрали пол"
        }

        return Player(
            name = name.text.toString().trim(),
            surname = surname.text.toString().trim(),
            otchestvo = otchestvo.text.toString().trim(),
            course = courseSpinner.selectedItemPosition + 1,
            difficulty = seekBar.progress,
            birthDay = day,
            birthMonth = month,
            zodiac = zodiac,
            birthYear = birthYear,
            gender = gender
        )
    }

    private fun showPlayerPreview(player: Player, tvResult: TextView, ivZodiac: ImageView) {
        tvResult.text =
            "${player.surname} ${player.name} ${player.otchestvo}\n" +
                    "Пол: ${player.gender}\n" +
                    "Курс: ${player.course}\n" +
                    "Сложность: ${player.difficulty}\n" +
                    "Дата: ${player.birthDay}.${player.birthMonth}.${player.birthYear}\n" +
                    "Зодиак: ${player.zodiac}"
        ivZodiac.setImageResource(zodiacImage(player.zodiac))
        ivZodiac.visibility = View.VISIBLE
    }

    private fun getZodiac(day: Int, month: Int): String {
        return when (month) {
            1 -> if (day < 20) "Козерог" else "Водолей"
            2 -> if (day < 19) "Водолей" else "Рыбы"
            3 -> if (day < 21) "Рыбы" else "Овен"
            4 -> if (day < 20) "Овен" else "Телец"
            5 -> if (day < 21) "Телец" else "Близнецы"
            6 -> if (day < 21) "Близнецы" else "Рак"
            7 -> if (day < 23) "Рак" else "Лев"
            8 -> if (day < 23) "Лев" else "Дева"
            9 -> if (day < 23) "Дева" else "Весы"
            10 -> if (day < 23) "Весы" else "Скорпион"
            11 -> if (day < 22) "Скорпион" else "Стрелец"
            12 -> if (day < 22) "Стрелец" else "Козерог"
            else -> ""
        }
    }

    private fun zodiacImage(zodiac: String): Int {
        return when (zodiac) {
            "Овен" -> R.drawable.oven
            "Телец" -> R.drawable.telec
            "Близнецы" -> R.drawable.twins
            "Рак" -> R.drawable.cancer
            "Лев" -> R.drawable.lie
            "Дева" -> R.drawable.deva
            "Весы" -> R.drawable.weight
            "Скорпион" -> R.drawable.scorp
            "Стрелец" -> R.drawable.shooter
            "Козерог" -> R.drawable.roga
            "Водолей" -> R.drawable.water
            "Рыбы" -> R.drawable.fish
            else -> R.drawable.ic_launcher_foreground
        }
    }
}