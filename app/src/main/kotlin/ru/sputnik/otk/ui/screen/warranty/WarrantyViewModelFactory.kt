package ru.sputnik.otk.ui.screen.warranty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.sputnik.otk.data.bitrix.BitrixClient

class WarrantyViewModelFactory(
    private val bitrixClient: BitrixClient,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == WarrantyViewModel::class.java) {
            "WarrantyViewModelFactory can create only WarrantyViewModel"
        }
        return WarrantyViewModel(bitrixClient) as T
    }
}
