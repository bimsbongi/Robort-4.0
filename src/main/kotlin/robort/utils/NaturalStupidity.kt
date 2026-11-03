package robort.utils

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class NaturalStupidityTexts(
    val loading: List<String>,
    val genericContents: List<String>,
    val openers: List<String>,
    val topicTerms: List<List<String>>,
    val emoji: List<String>,
)

private val naturalStupidityFile = loadResourceFile("/naturalStupidityTexts.json")?: error("Could not load naturalStupidityTexts.json")
private val naturalStupidityTexts = Json.decodeFromString<NaturalStupidityTexts>(naturalStupidityFile.readText())

private fun generateNaturalStupidityPromptContent(prompt: String) = Embed (
    title = ":robot: Prompt an die ND",
    description = prompt,
    color = 0x68c64d
)

suspend fun askNaturalStupidityCommand(interaction: SlashCommandInteractionEvent) {
    val prompt = interaction.getOption("prompt")?.asString?: error("No prompt for natural stupidity")
    val channel = interaction.channel

    interaction.replyEmbeds(generateNaturalStupidityPromptContent(prompt)).await()

    naturalStupidityTexts.loading.shuffled().take(2).forEach {
        channel.send("> $it").await()
        delay(1.seconds)
    }

    val text = buildString {
        append("**${naturalStupidityTexts.openers.random()}**\n\n")

        val structure = naturalStupidityTexts.topicTerms.random()

        val emoji = naturalStupidityTexts.emoji.shuffled()
        val contents = naturalStupidityTexts.genericContents.shuffled()

        structure.forEachIndexed { index, topic ->
            append("## ${emoji[index]} ${topic}\n")
            append("    ${contents[index]}\n")
        }
    }

    channel.send(text).await()
}