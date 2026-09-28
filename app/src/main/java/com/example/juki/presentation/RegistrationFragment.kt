package com.example.juki.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.juki.R
import com.example.juki.Player

class RegistrationFragment : Fragment() {

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
        val tvResult = view.findViewById<TextView>(R.id.tvResult)
        val ivZodiac = view.findViewById<ImageView>(R.id.ivZodiac)

        var selectedDay = 1
        var selectedMonth = 1
        var selectedYear = 2000

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDay = dayOfMonth
            selectedMonth = month + 1
            selectedYear = year
        }

        buttonShowZadiak.setOnClickListener {
            val day = selectedDay
            val month = selectedMonth
            val birthYear = selectedYear

            val zodiac = getZodiac(day, month)

            val gender = when (genderGroup.checkedRadioButtonId) {
                R.id.male -> "Мужчина"
                R.id.female -> "Женщина"
                else -> "Вы не выбрали пол"
            }

            val player = Player(
                    name = name.text.toString(),
                    surname = surname.text.toString(),
                    otchestvo = otchestvo.text.toString(),
                    course = courseSpinner.selectedItemPosition + 1,
                    difficulty = seekBar.progress,
                    birthDay = day,
                    birthMonth = month,
                    zodiac = zodiac,
                    birthYear = birthYear,
                    gender = gender
            )

            tvResult.text =
                    "${player.surname} ${player.name} ${player.otchestvo}\n" +
                            "Пол: ${player.gender}\n" +
                            "Курс: ${player.course}\n" +
                            "Сложность: ${player.difficulty}\n" +
                            "Дата: ${player.birthDay}.${player.birthMonth}.${player.birthYear}\n" +
                            "Зодиак: ${player.zodiac}"
            ivZodiac.visibility = View.VISIBLE
            ivZodiac.setImageResource(zodiacImage(zodiac))
        }
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