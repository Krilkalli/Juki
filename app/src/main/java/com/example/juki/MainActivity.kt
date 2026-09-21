package com.example.juki

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.registration_form)

        val name = findViewById<EditText>(R.id.name)
        val surname = findViewById<EditText>(R.id.surname)
        val otchestvo = findViewById<EditText>(R.id.othcestvo)
        val genderGroup = findViewById<RadioGroup>(R.id.myRadioGroup)
        val courseSpinner = findViewById<Spinner>(R.id.combobox)
        val seekBar = findViewById<SeekBar>(R.id.seekBar)
        val tvSelectedDate = findViewById<TextView>(R.id.tvSelectedDate)
        //val calendarView = findViewById<CalendarView>(R.id.calendarView)
        val btnPickDate = findViewById<Button>(R.id.btnPickDate)
        val buttonShowZadiak = findViewById<Button>(R.id.showZnak)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val ivZodiac = findViewById<ImageView>(R.id.ivZodiac)

        var selectedDay = 1
        var selectedMonth = 1
        var selectedYear = 2000


//        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
//            selectedDay = dayOfMonth
//            selectedMonth = month + 1
//            selectedYear = year
//

        fun showDatePickerDialog() {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val date = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                tvSelectedDate.text = date
            }, year, month, day).show()
        }

        btnPickDate.setOnClickListener { showDatePickerDialog() }

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
            "Овен"     -> R.drawable.oven
            "Телец"    -> R.drawable.telec
            "Близнецы" -> R.drawable.blizneci
            "Рак"      -> R.drawable.rak
            "Лев"      -> R.drawable.lev
            "Дева"     -> R.drawable.deva
            "Весы"     -> R.drawable.vesi
            "Скорпион" -> R.drawable.scorpion
            "Стрелец"  -> R.drawable.strelec
            "Козерог"  -> R.drawable.kozerog
            "Водолей"  -> R.drawable.vodoley
            "Рыбы"     -> R.drawable.ribi
            else       -> R.drawable.ic_launcher_foreground
        }
    }
}