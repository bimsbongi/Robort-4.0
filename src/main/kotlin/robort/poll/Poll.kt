package robort.poll

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.Label
import dev.minn.jda.ktx.interactions.components.Modal
import dev.minn.jda.ktx.interactions.components.SelectOption
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import dev.minn.jda.ktx.interactions.components.TextInput
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import robort.MessageActivity
import robort.jda
import robort.runningMessageActivities
import java.awt.Color
import kotlin.time.Duration.Companion.minutes

suspend fun listenForPollModalSubmission(event: ModalInteractionEvent) {
    if (!event.interaction.modalId.startsWith("poll_setup")) return
    createPollFromModal(event)
}

suspend fun createPollFromModal(event: ModalInteractionEvent) {
    event.deferEdit().await()
    val question = event.getValue("poll_setup_question")?.asString?: error("Cannot find poll_setup_question")
    val optionsRaw = event.getValue("poll_setup_options")?.asString?: error("Cannot find poll_setup_options")
    val options = optionsRaw.split("\n")
    val duration = event.getValue("poll_setup_time")?.asStringList?.firstOrNull()?.toIntOrNull()?: error("Cannot find poll_setup_time")
    val channel = jda.getChannel<MessageChannel>(event.interaction.modalId.split(":").last())?: error("Cannot find channel for poll")

    val poll = Poll(question, options, duration, channel, event.user)
    val message = poll.sendPollMessage()
    runningMessageActivities[message.idLong] = poll
    poll.waitToConclude()
    runningMessageActivities.remove(message.idLong)
}

fun pollSetupModal(targetChannelId: Long) = Modal(title = "Umfrage starten", id = "poll_setup:$targetChannelId") {
    components += Label (
        label = "Frage",
        child = TextInput(
            style = TextInputStyle.SHORT,
            customId = "poll_setup_question",
        )
    )

    components += Label (
        label = "Optionen - durch Zeilenumbruch getrennt",
        child = TextInput(
            style = TextInputStyle.PARAGRAPH,
            customId = "poll_setup_options"
        )
    )

    components += Label (
        label = "Dauer",
        child = StringSelectMenu(
            customId = "poll_setup_time",
            options = listOf(
                SelectOption("1 Minute", "1"),
                SelectOption("5 Minuten", "5"),
                SelectOption("1 Stunde", "60"),
                SelectOption("1 Tag", "1440")
            )
        )
    )
}

suspend fun startPollCommand(interaction: SlashCommandInteractionEvent) {
    interaction.replyModal(pollSetupModal(interaction.channel.idLong)).await()
}


class Poll(
    val question: String,
    val options: List<String>,
    val duration: Int,
    val channel: MessageChannel,
    val creator: User
) : MessageActivity {
    private data class UserRatings (val ratings: MutableMap<Int, Int>)
    private val cachedResponses = mutableMapOf<Long, UserRatings>()
    private val submittedResponses = mutableMapOf<Long, UserRatings>()

    suspend fun sendPollMessage() = channel.send {
        useComponentsV2 = true
        components += Container {
            accentColor = Color(0x4daaff)

            text("**${creator.effectiveName}**\n## \uD83D\uDDF3\uFE0F Umfrage\n### ${question}\n <t:${System.currentTimeMillis()/1000 + duration * 60}>")

            options.forEachIndexed { index, option ->
                text("**${index + 1}**. $option")
                actionRow {
                    stringSelectMenu(
                        customId = "poll_option:${index}",
                        options = listOf(
                            SelectOption("5 Sterne", value = "5", emoji = Emoji.fromUnicode("⭐")),
                            SelectOption("4 Sterne", value = "4", emoji = Emoji.fromUnicode("⭐")),
                            SelectOption("3 Sterne", value = "3", emoji = Emoji.fromUnicode("⭐")),
                            SelectOption("2 Sterne", value = "2", emoji = Emoji.fromUnicode("⭐")),
                            SelectOption("1 Stern", value = "1", emoji = Emoji.fromUnicode("⭐")),
                        )
                    )
                }
            }

            actionRow {
                button(ButtonStyle.PRIMARY, "poll_submit", "", Emoji.fromUnicode("\uD83D\uDCE9"))
            }
        }
    }.await()

    suspend fun waitToConclude() {
        delay(duration.minutes)
        conclude()
    }

    private fun conclude() {

    }

    override suspend fun processButtonClick(event: ButtonInteractionEvent) {
        if (event.user.idLong in submittedResponses.keys) {
            event.reply(":no_entry: Du hast auf diese Umfrage bereits geantwortet").setEphemeral(true).await()
            return
        }

        submittedResponses[event.user.idLong] = cachedResponses.getOrPut(event.user.idLong) { UserRatings(mutableMapOf()) }.copy()
        event.reply("\uD83D\uDCE9 Vielen Dank für deine Stimme!").setEphemeral(true).await()
    }

    override suspend fun processStringSelect(event: StringSelectInteractionEvent) {
        val userId = event.interaction.user.idLong
        val optionId = event.interaction.component.customId.split(":").last().toInt()
        val rating = event.selectedOptions.first().value.toInt()

        cachedResponses.getOrPut(userId) { UserRatings(mutableMapOf()) }.ratings[optionId] = rating
    }

}


