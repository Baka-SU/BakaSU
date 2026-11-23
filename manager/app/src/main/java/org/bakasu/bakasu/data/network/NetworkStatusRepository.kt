package org.bakasu.bakasu.data.network

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.InetAddress

class NetworkStatusRepository(
    private val application: Application,
) {
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun needsLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            application.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val isLocalTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return isLocalTransport && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun isLocalNetworkAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return false
        if (address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return true

        val bytes = address.address
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        if (bytes.size == 4) {
            if ((first == 100 && second in 64..127) || bytes.all { it.toInt() == -1 }) return true
        } else if (first == 0xfc || first == 0xfd) {
            return true
        }

        val network = connectivityManager.activeNetwork ?: return false
        return connectivityManager.getLinkProperties(network)?.routes.orEmpty().any {
            !it.isDefaultRoute && it.gateway?.isAnyLocalAddress != false && it.matches(address)
        }
    }

    fun isAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val hasTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return hasTransport &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
