package robort.quiz

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import robort.utils.ChannelActivity

@Serializable
data class QuestionOption(
    val id: Int,
    val name: String = "",
    val emoji: String,
    val weight: Int = 0,
    val secondaryWeight: Int = 0,
)

@Serializable
data class Question (
    val textContent: String = "",
    val imagePath: String = "",
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

class Quiz (
    val questions: List<Question>,
    override val channel: MessageChannel,
    val player: User
) : ChannelActivity {
    val currentIndex = 0



}