package robort

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent

interface ChannelActivity {
    suspend fun processButtonClick(event: ButtonInteractionEvent) {}
    suspend fun processStringSelect(event: StringSelectInteractionEvent) {}

    suspend fun processMessageReply(message: Message) {}
}

val runningChannelActivities = mutableMapOf<Long, ChannelActivity>()


interface MessageActivity {
    suspend fun processButtonClick(event: ButtonInteractionEvent) {}
    suspend fun processStringSelect(event: StringSelectInteractionEvent) {}
    suspend fun processMessageReply(event: MessageReceivedEvent) {}
    suspend fun processEntitySelect(event: EntitySelectInteractionEvent) {}
}

val runningMessageActivities = mutableMapOf<Long, MessageActivity>()