package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.infrastructure.adapters.database.AppDatabase
import com.example.infrastructure.adapters.database.RoomStorageAdapter
import com.example.infrastructure.adapters.ui.DashboardScreen
import com.example.infrastructure.adapters.ui.HabitViewModel
import com.example.ui.theme.HabitEngineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Instantiate the background SQL-Room Database Engine
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "habitengine_habits_ledger.db"
        )
        .fallbackToDestructiveMigration() // safe sandbox environment migrations fallback
        .build()

        // 2. Wrap via Hexagonal StoragePort Interface Adapter
        val storageAdapter = RoomStorageAdapter(database)

        // 3. Simple elegant Constructor-Injection Factory for ViewModel Setup
        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return HabitViewModel(storageAdapter) as T
                }
                throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
            }
        }

        // 4. Retrieve state-retaining ViewModel
        val viewModel: HabitViewModel by viewModels { viewModelFactory }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // 5. Apply adaptive custom Theme (Cyberpunk Focus / Sunset Calm)
            HabitEngineTheme(themeMode = uiState.themeMode) {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
