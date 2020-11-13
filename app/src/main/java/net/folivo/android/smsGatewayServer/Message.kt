package net.folivo.android.smsGatewayServer

class Message(
    val id: Long,
    val number: String,
    val body: String,
    val dateReceived: String,
    val dateSent: String,
    val type: String
)