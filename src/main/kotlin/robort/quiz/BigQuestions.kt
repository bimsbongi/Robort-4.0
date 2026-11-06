package robort.quiz

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.Embed
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import robort.runningActivities
import robort.utils.loadResourceFile
import java.awt.Color

private val bigQuestionsQuizDataFile = loadResourceFile("/Quiz/bigQuestions.json")?: error("Could not load Quiz/bigQuestions.json")
val bigQuestionsQuizData = Json.decodeFromString<QuizData>(bigQuestionsQuizDataFile.readText())

suspend fun requestBigQuestionsQuizCommand(interaction: SlashCommandInteractionEvent) {
    if (runningActivities.containsKey(interaction.channel.idLong)) {
        interaction.reply(":no_entry: In diesem Kanal läuft noch eine andere Aktivität. Warte kurz oder nutze einen anderen Kanal.").setEphemeral(true).await()
        return
    }

    val quiz = Quiz(bigQuestionsQuizData, interaction.channel, interaction.user, BigQuestionsQuizResultCalculator, true)
    runningActivities[quiz.channel.idLong] = quiz
    quiz.startQuizCommand(interaction)
}

data object BigQuestionsQuizResultCalculator : QuizResultCalculator {
    override fun calculateResults(quiz: Quiz) = Container {
        accentColor = Color(quiz.quizData.accentColor)

        text(content = "## Vielen Dank für deine Teilnahme!\nDie anderen haben halt keine Ahnung von der Welt :wink:")
    }
}