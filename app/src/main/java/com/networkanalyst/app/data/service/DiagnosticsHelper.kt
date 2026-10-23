package com.networkanalyst.app.data.service

import com.networkanalyst.app.data.model.NetworkInfoData

interface DiagnosticsHelper {
    fun getNetworkInfoSnapshot(): NetworkInfoData
}
