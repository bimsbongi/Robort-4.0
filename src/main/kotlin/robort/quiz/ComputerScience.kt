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

private val computerScienceQuizDataFile = loadResourceFile("/Quiz/computerScience.json")?: error("Could not load Quiz/computerScience.json")
val computerScienceQuizData = Json.decodeFromString<QuizData>(computerScienceQuizDataFile.readText())

suspend fun requestComputerScienceQuizCommand(interaction: SlashCommandInteractionEvent) {
    if (runningActivities.containsKey(interaction.channel.idLong)) {
        interaction.reply(":no_entry: In diesem Kanal läuft noch eine andere Aktivität. Warte kurz oder nutze einen anderen Kanal.").setEphemeral(true).await()
        return
    }

    val quiz = Quiz(computerScienceQuizData, interaction.channel, interaction.user, ComputerScienceQuizResultCalculator, false)
    runningActivities[quiz.channel.idLong] = quiz
    quiz.startQuizCommand(interaction)
}

data object ComputerScienceQuizResultCalculator : QuizResultCalculator {
    override fun calculateResults(quiz: Quiz) = Container {
        val topPoints = max(0, quiz.weightSum - 30)
        val scaleIndex = 4 - min(4, (topPoints / 50));
        val scaleTemplate = arrayOf(
            ":skull_crossbones: **Unheilbar**",
            "🟥 **Risikopatient**",
            "🟧 **Im Frühstadium**",
            "🟨 **Verdachtsfall**",
            "🟩 **Normaler Mensch**"
        )

        val scale = buildString {
            scaleTemplate.forEachIndexed { idx, point ->
                if (scaleIndex == idx) append("\n**Du =>** $point\n⠀ ⠀ ⠀\\|\\|\\|\\|")
                else append("\n⠀ ⠀ ⠀$point\n⠀ ⠀ ⠀\\|\\|\\|\\|")
            }
        }

        text(content = "## :dna: Offizielle Diagnose\nDer Informatik-o-mat hat für ${quiz.player.asMention} ergeben: \n\n***${quiz.weightSum} Punkte***\n$scale")
        accentColor = Color(quiz.quizData.accentColor)
    }
}