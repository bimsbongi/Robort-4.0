package robort.quiz

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.edit
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.utils.FileUpload
import robort.ChannelActivity
import robort.runningActivities
import robort.utils.loadResourceFile
import java.awt.Color
import kotlin.time.Duration.Companion.seconds

sealed interface QuizResultCalculator {
    fun calculateResults(quiz: Quiz): MessageEmbed
}

@Serializable
data class QuestionOption(
    val name: String = "",
    val emoji: String,
    val weight: Int = 0,
    val secondaryWeight: Int = 0,
)

@Serializable
data class Question (
    val id: Int,
    val textContent: String = "",
    val imagePath: String? = null,
    val options: List<QuestionOption>,
)

@Serializable
data class QuizData (
    val title: String,
    val emoji: String,
    val accentColor: Int,

    val description: String,
    val questions: List<Question>,
)

data class UserQuizAnswer(val userId: Long, val responses: Map<Int, Int>)

class Quiz (
    val quizData: QuizData,
    override val channel: MessageChannel,
    val player: User,
    val resultCalculator: QuizResultCalculator,
    val revealOtherUserAnswers: Boolean = false,
    val otherUserAnswers: List<UserQuizAnswer> = emptyList()
) : ChannelActivity {
    var currentIndex = 0
    var lastQuestionMessage: Message? = null
    val answers = mutableMapOf<Int, Int>()
    var weightSum = 0
    var secondaryWeightSum = 0
    val shuffledQuestions = quizData.questions.shuffled()

    private fun startEmbed() = Embed (
        title = "${quizData.emoji} ${quizData.title}",
        description = quizData.description,
        authorName = player.effectiveName,
        authorIcon = player.effectiveAvatarUrl,
        color = quizData.accentColor
    )

    suspend fun startQuizCommand(interaction: SlashCommandInteractionEvent) {
        interaction.replyEmbeds(startEmbed()).await()
        delay(1.seconds)
        sendNextQuestion()
    }

    private suspend fun processAnswer(answerIndex: Int) {
        if (currentIndex >= shuffledQuestions.size) error("Quiz has ended")

        answers[shuffledQuestions[currentIndex].id] = answerIndex

        weightSum += shuffledQuestions[currentIndex].options[answerIndex].weight
        secondaryWeightSum += shuffledQuestions[currentIndex].options[answerIndex].secondaryWeight

        // TODO reveal other user answers

        lastQuestionMessage?.edit {
            val imagePath = shuffledQuestions[currentIndex].imagePath

            useComponentsV2 = true
            components += Container {
                accentColor = Color(quizData.accentColor)
                text(content = "### ${quizData.emoji} Frage Nr. ${currentIndex + 1}")

                if (imagePath != null) {
                    mediaGallery {
                        item("attachment://$imagePath")
                    }
                }
                text(content = shuffledQuestions[currentIndex].textContent)

                actionRow {
                    shuffledQuestions[currentIndex].options.forEachIndexed { idx, option ->
                        button(
                            if (answerIndex == idx) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                            idx.toString(),
                            option.name,
                            Emoji.fromUnicode(option.emoji),
                            true
                        )
                    }
                }
            }
        }?.await()

        currentIndex += 1

        if (currentIndex < shuffledQuestions.size) {
            sendNextQuestion()
            return
        }

        val resultEmbed = resultCalculator.calculateResults(this)
        channel.sendMessageEmbeds(resultEmbed).await()
        runningActivities.remove(channel.idLong)
    }

    suspend fun sendNextQuestion() {
        lastQuestionMessage = channel.send {
            val imagePath = shuffledQuestions[currentIndex].imagePath

            useComponentsV2 = true
            components += Container {
                accentColor = Color(quizData.accentColor)
                text(content = "### ${quizData.emoji} Frage Nr. ${currentIndex + 1}")

                if (imagePath != null) {
                    mediaGallery {
                        item("attachment://$imagePath")
                    }
                    val imageFile = loadResourceFile(imagePath)?: error("Failed to load image file $imagePath")
                    files += FileUpload.fromData(imageFile, imagePath)
                }
                text(content = shuffledQuestions[currentIndex].textContent)

                actionRow {
                    shuffledQuestions[currentIndex].options.forEachIndexed { idx, option ->
                        button(
                            ButtonStyle.SECONDARY,
                            idx.toString(),
                            option.name,
                            Emoji.fromUnicode(option.emoji)
                        )
                    }
                }
            }
        }.await()
    }

    override suspend fun processButtonClick(event: ButtonInteractionEvent) {
        if (event.message.id == lastQuestionMessage?.id && event.user.idLong == player.idLong)
            processAnswer(event.customId.toInt())
    }

    override suspend fun processMessageReply(message: Message) {}
}