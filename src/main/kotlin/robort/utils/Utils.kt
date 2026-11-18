package robort.utils

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import java.io.File
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Duration

fun JDA.onSlashCommand(name: String, timeout: Duration? = null, consumer: suspend CoroutineEventListener.(SlashCommandInteractionEvent) -> Unit) = listener<SlashCommandInteractionEvent>(timeout=timeout) {
    if (it.name == name)
        consumer(it)
}

fun loadResourceFile(path: String): File? = object {}.javaClass.getResource(path)?.file?.let { File(it) }

fun envOrDefault(name: String, default: String): String = env(name) ?: default
fun env(name: String): String? = System.getenv(name)

fun abbreviateNumber(number: Long): String {
    val absNumber = abs(number)

    if (absNumber >= 1000000000) return "${round((number/1000000000).toDouble())}B"
    if (absNumber >= 1000000) return "{${round((number/1000000).toDouble())}M"
    if (absNumber >= 1000) return "${round((number/1000).toDouble())}K"
    return number.toString()
}

fun splitTextIntoLines(text: String, targetCharsPerLine: Int): List<String> {
    val words = text.split(" ")

    var currentLine = words.first()
    val lines = mutableListOf<String>()

    for (word in words.subList(1, words.size)) {
        if (currentLine.length + word.length + 1 <= targetCharsPerLine) {
            currentLine += " $word"
        } else {
            lines.add(currentLine)
            currentLine = word
        }
    }
    lines.add(currentLine)

    return lines
}