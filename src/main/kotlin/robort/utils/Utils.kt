package robort.utils

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.time.Duration

fun JDA.onSlashCommand(name: String, timeout: Duration? = null, consumer: suspend CoroutineEventListener.(SlashCommandInteractionEvent) -> Unit) = listener<SlashCommandInteractionEvent>(timeout=timeout) {
    if (it.name == name)
        consumer(it)
}

interface ChannelActivity {
    val channel: MessageChannel
}