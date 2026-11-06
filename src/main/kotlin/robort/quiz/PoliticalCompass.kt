package robort.quiz


import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.Embed
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import robort.runningActivities
import robort.utils.loadResourceFile
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

private val politicalCompassQuizDataFile = loadResourceFile("/Quiz/politicalCompass.json")?: error("Could not load Quiz/politicalCompass.json")
val politicalCompassQuizData = Json.decodeFromString<QuizData>(politicalCompassQuizDataFile.readText())

suspend fun requestPoliticalCompassQuizCommand(interaction: SlashCommandInteractionEvent) {
    if (runningActivities.containsKey(interaction.channel.idLong)) {
        interaction.reply(":no_entry: In diesem Kanal läuft noch eine andere Aktivität. Warte kurz oder nutze einen anderen Kanal.").setEphemeral(true).await()
        return
    }

    val quiz = Quiz(politicalCompassQuizData, interaction.channel, interaction.user, PoliticalCompassResultCalculator, false)
    runningActivities[quiz.channel.idLong] = quiz
    quiz.startQuizCommand(interaction)
}

data object PoliticalCompassResultCalculator : QuizResultCalculator {
    override fun calculateResults(quiz: Quiz) = Container {

        val scaledX = quiz.weightSum / 20L
        val scaledY = quiz.secondaryWeightSum / 20L

        val xPoint = Math.clamp((if (scaledX >= 0) scaledX + 1 else scaledX), -3, 3)
        val yPoint = Math.clamp((if (scaledY >= 0) scaledY + 1 else scaledY), -3, 3)

        fun compassBaseColor(x: Int, y: Int): String {
            return if (x > 0) {
                if (y < 0) ":yellow_square:" else ":blue_square:"
            } else {
                if (y < 0) ":green_square:" else ":red_square:"
            }
        }

        var compass = """⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀ ⠀         Dattel Diktatur
        ⠀
        ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀[-3, 3][-2, 3][-1, 3]║[1, 3][2, 3][3, 3]
        ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀[-3, 2][-2, 2][-1, 2]║[1, 2][2, 2][3, 2]
        ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀[-3, 1][-2, 1][-1, 1]║[1, 1][2, 1][3, 1]
          Gemüse Gesellschaft ⠀ ⠀ ══════╬══════ ⠀ Trauben Theokratie
        ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀[-3, -1][-2, -1][-1, -1]║[1, -1][2, -1][3, -1]
        ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀[-3, -2][-2, -2][-1, -2]║[1, -2][2, -2][3, -2]
        ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀[-3, -3][-2, -3][-1, -3]║[1, -3][2, -3][3, -3]
        ⠀
        ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀V-Bucks Verwaltung"""

        repeat(7) { xRaw ->
            repeat(7) { yRaw ->
                val x = xRaw - 3
                val y = yRaw - 3
                println("replacing [$x, $y]")
                println("star at $xPoint, $yPoint")
                compass = compass.replace("[$x, $y]", if (x == xPoint && y == yPoint) ":star:" else compassBaseColor(x, y))
            }
        }

        text(content = "## :compass: Ergebnis des Tests\n" +
                "Gemüse Gesellschaft/Trauben Theokratie **${quiz.weightSum} Punkte**\n" +
                "V-Bucks Verwaltung/Dattel Diktatur **${quiz.secondaryWeightSum} Punkte**\n\n" +
            compass
        )
        accentColor = Color(quiz.quizData.accentColor)
    }
}