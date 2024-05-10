package com.kurai.mangasamurai2.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Please use the menu to choose a folder, then wait until the cutting is done."
    }
    val text: LiveData<String> = _text
}