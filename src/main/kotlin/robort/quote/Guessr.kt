package robort.quote

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

suspend fun quoteGuessrCommand(interaction: SlashCommandInteractionEvent) {
    val quote = selectGuessrQuote(interaction.guild?.idLong?: -1)

    if (quote == null) {
        interaction.reply(":no_entry: Es konnte kein Zitat gefunden werden").setEphemeral(true).await()
        return
    }

    interaction.reply(
        quote.content.joinToString(separator = "\n") { "${it.first} - ||${it.second}||" }
    ).await()
}