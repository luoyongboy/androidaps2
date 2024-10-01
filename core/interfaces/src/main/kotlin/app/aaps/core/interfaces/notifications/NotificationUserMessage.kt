package app.aaps.core.interfaces.notifications

class NotificationUserMessage(text: String) : Notification() {

    init {
        var hash = text.hashCode()
        if (hash < USER_MESSAGE) hash += USER_MESSAGE
        id = hash
        date = System.currentTimeMillis()
        this.text = text
        level = URGENT
    }
}

class NotificationInfoMessage(text: String) : Notification() {

    init {
        var hash = text.hashCode()
        if (hash < USER_MESSAGE) hash += USER_MESSAGE
        id = hash
        date = System.currentTimeMillis()
        this.text = text
        level = INFO
    }
}