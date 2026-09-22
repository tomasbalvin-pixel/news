package cz.balvin.news.ui.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import cz.balvin.news.AppContainer
import cz.balvin.news.NewsApplication

/** Resolves the application container from the extras Compose already supplies. */
val CreationExtras.appContainer: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NewsApplication).container
