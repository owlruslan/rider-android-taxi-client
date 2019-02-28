package com.github.owlruslan.rider.ui.map

import androidx.annotation.Nullable
import com.github.owlruslan.rider.di.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class MapPresenter @Inject constructor() : MapContract.Presenter {

    @Nullable
    private var view: MapContract.View? = null

    override fun takeView(view: MapContract.View) {
        this.view = view
    }

    override fun dropView() {
        view = null
    }

    override fun openSearchView() {
        view?.showSearchView()
    }
}
