package robort.quiz

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.MentionConfig
import dev.minn.jda.ktx.messages.Mentions
import dev.minn.jda.ktx.messages.edit
import dev.minn.jda.ktx.messages.reply_
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.entities.Message
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
    fun calculateResults(quiz: Quiz): Container
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
    val questionName: String,

    val description: String,
    val questions: List<Question>,
)

data class UserQuizAnswer(val answers: Map<Int, Int>)

class Quiz (
    val quizData: QuizData,
    override val channel: MessageChannel,
    val player: User,
    val resultCalculator: QuizResultCalculator,
    val revealGuildMemberAnswers: Boolean = false,
    val guildMemberAnswers: MutableMap<Long, UserQuizAnswer> = mutableMapOf()
) : ChannelActivity {
    var currentIndex = 0
    var lastQuestionMessage: Message? = null
    val answers = mutableMapOf<Int, Int>()
    var weightSum = 0
    var secondaryWeightSum = 0
    val shuffledQuestions = quizData.questions.shuffled()

    suspend fun startQuizCommand(interaction: SlashCommandInteractionEvent) {
        interaction.reply_ {
            useComponentsV2 = true
            components += startContainer()
        }.await()
        delay(1.seconds)
        sendNextQuestion()
    }

    suspend fun startQuizRemote() {
        channel.send {
            useComponentsV2 = true
            components += startContainer()
        }.await()
        delay(1.seconds)
        sendNextQuestion()
    }

    private suspend fun processAnswer(answerIndex: Int) {
        if (currentIndex >= shuffledQuestions.size) error("Quiz has ended")

        answers[shuffledQuestions[currentIndex].id] = answerIndex

        weightSum += shuffledQuestions[currentIndex].options[answerIndex].weight
        secondaryWeightSum += shuffledQuestions[currentIndex].options[answerIndex].secondaryWeight

        guildMemberAnswers[player.idLong] = UserQuizAnswer(answers)

        lastQuestionMessage?.edit {
            useComponentsV2 = true
            components += questionContainer(answerIndex) { fileUpload -> files += fileUpload }
        }?.await()

        currentIndex += 1

        if (currentIndex < shuffledQuestions.size) {
            sendNextQuestion()
            return
        }

        channel.send {
            useComponentsV2 = true
            components += resultCalculator.calculateResults(this@Quiz)
        }.await()

        runningActivities.remove(channel.idLong)
    }

    override suspend fun processButtonClick(event: ButtonInteractionEvent) {
        if (event.message.id == lastQuestionMessage?.id && event.user.idLong == player.idLong)
            processAnswer(event.customId.toInt())
    }

    override suspend fun processMessageReply(message: Message) {}

    private fun startContainer() = Container {
        accentColor = Color(quizData.accentColor)
        text("**${player.effectiveName}**\n## ${quizData.emoji} ${quizData.title}\n${quizData.description}")
    }

    private fun questionContainer(answerIndex: Int?, attachment:(FileUpload) -> Unit) = Container {
        accentColor = Color(quizData.accentColor)
        text(content = "### ${quizData.emoji} ${quizData.questionName} Nr. ${currentIndex + 1}")

        val imagePath = shuffledQuestions[currentIndex].imagePath
        if (imagePath != null) {
            mediaGallery {
                item("attachment://$imagePath")

                val imageFile = loadResourceFile(imagePath)?: error("Failed to load image file $imagePath")
                attachment(FileUpload.fromData(imageFile, imagePath))
            }
        }
        text(shuffledQuestions[currentIndex].textContent)

        actionRow {
            shuffledQuestions[currentIndex].options.forEachIndexed { idx, option ->
                button(
                    if (answerIndex == idx) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                    idx.toString(),
                    option.name,
                    Emoji.fromUnicode(option.emoji),
                    answerIndex != null
                )
            }
        }

        if (revealGuildMemberAnswers && answerIndex != null) {
            text(
                buildString {
                    val questionId = shuffledQuestions[currentIndex].id
                    shuffledQuestions[currentIndex].options.forEachIndexed { idx, option ->
                        val members = guildMemberAnswers.filter { it.value.answers[questionId] == idx }.keys

                        append("\n**${option.emoji} ${option.name}**\n")

                        members.forEach { append("<@$it>\n")}
                    }
                }
            )
        }
    }

    private suspend fun sendNextQuestion() {
        lastQuestionMessage = channel.send(mentions = Mentions.none()) {
            useComponentsV2 = true
            components += questionContainer(null) { fileUpload -> files += fileUpload }
        }.await()
    }
}