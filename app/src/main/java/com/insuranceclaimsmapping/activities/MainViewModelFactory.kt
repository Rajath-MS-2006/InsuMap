package com.insuranceclaimsmapping.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.insuranceclaimsmapping.utils.PrefManager

class MainViewModelFactory(private val prefManager: PrefManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(prefManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
