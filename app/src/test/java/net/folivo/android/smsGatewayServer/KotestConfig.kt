package net.folivo.android.smsGatewayServer

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.experimental.robolectric.RobolectricExtension

class KotestConfig : AbstractProjectConfig() {
    override fun extensions(): List<Extension> = super.extensions() + RobolectricExtension()
}