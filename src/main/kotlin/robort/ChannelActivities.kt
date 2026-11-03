package robort

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

interface ChannelActivity {
    val channel: MessageChannel

    suspend fun processButtonClick(event: ButtonInteractionEvent)

    suspend fun processMessageReply(message: Message)
}

val runningActivities = mutableMapOf<Long, ChannelActivity>()