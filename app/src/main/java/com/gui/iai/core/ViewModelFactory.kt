package com.gui.iai.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gui.iai.IaiApplication

/** Cria fábricas de ViewModel com acesso ao AppContainer. */
inline fun <reified VM : ViewModel> iaiFactory(
    crossinline create: CreationExtras.(AppContainer) -> VM
): ViewModelProvider.Factory = viewModelFactory {
    initializer<VM> {
        val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IaiApplication
        create(this, app.container)
    }
}
