package robort.utils

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.components.Label
import dev.minn.jda.ktx.interactions.components.Modal
import dev.minn.jda.ktx.interactions.components.SelectOption
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import dev.minn.jda.ktx.interactions.components.TextInput
import dev.minn.jda.ktx.messages.Embed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import robort.jda
import robort.poll.createPollFromModal
import kotlin.error
import kotlin.random.Random

@Serializable
private data class OracleTexts(
    val triggers: List<String>,
    val inverters: List<String>,
    val texts: List<String>,
)

private val oracleFile = loadResourceFile("/oracleTexts.json")?: error("Could not load oracleTexts.json")
private val oracleTexts = Json.decodeFromString<OracleTexts>(oracleFile.readText())

private fun generateFirmament(): String {
    val symbols = arrayOf(":sunny:", ":crescent_moon:", ":ringed_planet:", ":earth_africa:", ":comet:")

    val tiles = Array(100) { "⬛" }
    symbols.forEach { symbol ->
        tiles[Random.nextInt(0, 100)] = symbol
    }

    return buildString {
        repeat(10) {
            append(tiles.sliceArray((it * 10)..<(it * 10 + 10)).joinToString(""))
            append("\n")
        }
    }
}

private fun generateOracleResult(question: String) = buildString {
    append("\n")
    append(oracleTexts.texts.shuffled().take(2).joinToString("\n"))
    append("\n")
    append("\n")

    val result = if (oracleTexts.triggers.any { question.contains(it) }) {
         oracleTexts.inverters.sumOf { question.split(it).size - 1 } % 2 == 0
    } else {
        Random.nextBoolean()
    }

    if (result) {
        append(":white_check_mark: Wahr")
    } else {
        append(":x: Falsch")
    }
}

private fun generateOracleContent(question: String) =
    Embed (
        title = ":dizzy:  Orakel",
        color = 0xd29800,
        description = question,
    ){
        field(name = "Stand der Gestirne", value = generateFirmament(), inline = true)
        field(name = "Interpretation", value = generateOracleResult(question), inline = true)
    }

suspend fun askOracleCommand(interaction: SlashCommandInteractionEvent) {
    val question = interaction.getOption("question")?.asString?: error("No oracle question")
    interaction.replyEmbeds(generateOracleContent(question)).await()
}

suspend fun askOracleButton(event: ButtonInteractionEvent, targetChannel: MessageChannel) {
    event.replyModal(
        Modal(title = "Orakel befragen", id = "ask_oracle:${targetChannel.idLong}") {
            components += Label (
                label = "Frage",
                child = TextInput(
                    style = TextInputStyle.SHORT,
                    customId = "oracle_question",
                )
            )
        }
    ).await()
}

suspend fun listenForOracleModalSubmission(event: ModalInteractionEvent) {
    if (!event.interaction.modalId.startsWith("ask_oracle")) return

    val question = event.getValue("oracle_question")?.asString?: error("Cannot find oracle_question")
    val channel = jda.getChannel<MessageChannel>(event.interaction.modalId.split(":").last())?: error("Cannot find channel for oracle")

    event.deferEdit().await()
    channel.sendMessageEmbeds(generateOracleContent(question)).await()
}