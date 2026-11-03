package robort.utils

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

suspend fun showProfileColorCommand(interaction: SlashCommandInteractionEvent) {
    val user = interaction.getOption("user")?.asUser?: error("User for profile color query does not exist.")
    val colorId = ((user.id.toBigInteger().shiftRight(22)) % 6.toBigInteger()).toInt()

    val colors = arrayOf("Blau", "Grau", "Grün", "Gelb", "Rot", "Pink")
    val colorSymbols = arrayOf(":blue_heart:", ":grey_heart:", ":green_heart:", ":yellow_heart:", ":red_heart:", ":pink_heart:")

    val color = colors[colorId]
    val colorSymbol = colorSymbols[colorId]

    interaction.reply("**$colorSymbol ${user.asMention} ist ${color}! ${colorSymbol}**").await()
}