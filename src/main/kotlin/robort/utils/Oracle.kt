package robort.utils

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.Embed
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.error
import kotlin.random.Random

@Serializable
private data class OracleTexts(
    val triggers: List<String>,
    val inverters: List<String>,
    val texts: List<String>,
)

private val oracleFile = object {}.javaClass.getResource("/oracleTexts.json")?: error("Could not load oracleTexts.json")
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