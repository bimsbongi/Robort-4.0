package robort.quiz

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import robort.runningChannelActivities
import robort.utils.loadResourceFile
import java.awt.Color

private val artQuizDataFile = loadResourceFile("/Quiz/art.json")?: error("Could not load Quiz/art.json")
val artQuizData = Json.decodeFromString<QuizData>(artQuizDataFile.readText())

suspend fun requestArtQuizCommand(interaction: SlashCommandInteractionEvent) {
    if (runningChannelActivities.containsKey(interaction.channel.idLong)) {
        interaction.reply(":no_entry: In diesem Kanal läuft noch eine andere Aktivität. Warte kurz oder nutze einen anderen Kanal.").setEphemeral(true).await()
        return
    }

    val quiz = Quiz(artQuizData, interaction.channel, interaction.user, ArtQuizResultCalculator, interaction.guild, QuizType.ART)
    quiz.startQuizCommand(interaction)
}

data object ArtQuizResultCalculator : QuizResultCalculator {
    override fun calculateResults(quiz: Quiz) = Container {
        accentColor = Color(quiz.quizData.accentColor)

        text(content = "## Vielen Dank für deine Teilnahme!\nDie anderen haben halt keine Ahnung von echter Kunst :wink:")
    }
}