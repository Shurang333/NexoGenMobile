package com.example.nexogen

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var topStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)
        topStatus = findViewById(R.id.topStatus)

        // стартовый экран
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
            bottomNav.selectedItemId = R.id.nav_home
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_sessions -> SessionsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Вызывается фрагментами для обновления статуса в верхнем баре */
    fun setTopStatus(text: String) {
        topStatus.text = text
    }

    /** Запустить сессию — переключить на фрагмент активной сессии, спрятать нижний бар */
    fun enterSession(peerId: String, server: String, fps: Int, quality: Int) {
        bottomNav.visibility = android.view.View.GONE
        val f = SessionFragment.newInstance(peerId, server, fps, quality)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f)
            .commit()
    }

    /** Выйти из сессии — вернуть нижний бар */
    fun exitSession() {
        bottomNav.visibility = android.view.View.VISIBLE
        loadFragment(HomeFragment())
        bottomNav.selectedItemId = R.id.nav_home
    }
}