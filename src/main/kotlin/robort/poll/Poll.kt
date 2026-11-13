package robort.poll

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.Label
import dev.minn.jda.ktx.interactions.components.Modal
import dev.minn.jda.ktx.interactions.components.SelectOption
import dev.minn.jda.ktx.interactions.components.StringSelectMenu
import dev.minn.jda.ktx.interactions.components.TextInput
import dev.minn.jda.ktx.messages.edit
import dev.minn.jda.ktx.messages.reply_
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Message
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
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes

suspend fun listenForPollModalSubmission(event: ModalInteractionEvent) {
    if (!event.interaction.modalId.startsWith("poll_setup")) return
    createPollFromModal(event)
}

suspend fun createPollFromModal(event: ModalInteractionEvent) {
    val question = event.getValue("poll_setup_question")?.asString?: error("Cannot find poll_setup_question")
    val optionsRaw = event.getValue("poll_setup_options")?.asString?: error("Cannot find poll_setup_options")
    val options = optionsRaw.split("\n")
    val duration = event.getValue("poll_setup_time")?.asStringList?.firstOrNull()?.toIntOrNull()?: error("Cannot find poll_setup_time")
    val channel = jda.getChannel<MessageChannel>(event.interaction.modalId.split(":").last())?: error("Cannot find channel for poll")

    if (options.size < 2) {
        event.reply(":no_entry: Es müssen mindestens zwei Optionnen zur Wahl stehen").setEphemeral(true).await()
        return
    }

    event.deferEdit().await()

    val poll = Poll(question, options, duration, channel, event.user)
    poll.sendPollMessage()
    runningMessageActivities[poll.message!!.idLong] = poll
    poll.waitToConclude()
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

val pollColor = Color(0x4daaff)

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

    var message: Message? = null
    private var concluded = false

    suspend fun sendPollMessage() {
        message = channel.send {
            useComponentsV2 = true
            components += pollContainer(true)
        }.await()
    }

    suspend fun waitToConclude() {
        delay(duration.minutes)
        if (!concluded) conclude()
    }

    private fun pollContainer(active: Boolean) = Container {
        accentColor = pollColor

        text("**${creator.effectiveName}**\n## \uD83D\uDDF3\uFE0F Umfrage\n### ${question}\n")

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
                    ),
                    disabled = !active
                )
            }
        }
        separator()
        actionRow {
            button(ButtonStyle.PRIMARY, "poll_submit", "Abstimmen", Emoji.fromUnicode("\uD83D\uDCE9"), !active)
            button(ButtonStyle.DANGER, "poll_end", "Beenden", Emoji.fromUnicode("\uD83D\uDED1"),!active)
            button(ButtonStyle.SECONDARY, "poll_info", "Infos", Emoji.fromUnicode("\uD83D\uDCDA"))
        }

        text(if (active) "Umfrage endet <t:${System.currentTimeMillis() / 1000 + duration * 60}:R>" else "*Umfrage ist beendet*")
    }

    private suspend fun conclude() {
        concluded = true

        val (winnerIds, usedMatchups) = evaluate()
        val winnerNames = winnerIds.map { id -> options[id] }
        val matchupsText = usedMatchups.joinToString("\n") { "${options[it.winnerId]} **>** ${options[it.looserId]} (${(it.winningPercent * 100).toInt()}%)" }

        message?.edit {
            useComponentsV2 = true
            components += pollContainer(false)
        }?.await()

        channel.send {
            useComponentsV2 = true
            components += Container {
                accentColor = pollColor
                text("## 🗳️ Ergebnis\n### ${winnerNames.joinToString(", ")}")
                separator()
                text(matchupsText)
                separator()
                text("${submittedResponses.size} Stimme(n)")
            }
        }.setMessageReference(message?.idLong?: 0L).await()

        runningMessageActivities.remove(message?.idLong)
    }

    private fun evaluate(): Pair<List<Int>, List<OptionMatchup>> {
        val matchups = createMatchups()
        val sortedMatchups = matchups.filter { it.winningPercent > 0.5 }.shuffled().sortedByDescending { it.winningPercent }
        val optionTally = List(options.size) { i -> Option(i) }
        val usedMatchups = mutableListOf<OptionMatchup>()

        sortedMatchups.forEach { matchup ->
            val winner = optionTally[matchup.winnerId]
            val looser = optionTally[matchup.looserId]

            if (winner.inferiorTo.contains(looser) || looser.inferiorTo.contains(winner)) return@forEach

            val upperGroup = winner.inferiorTo + winner
            val lowerGroup = looser.superiorTo + looser

            upperGroup.forEach { it.superiorTo.addAll(lowerGroup) }
            lowerGroup.forEach { it.inferiorTo.addAll(upperGroup) }

            usedMatchups.add(matchup)
        }

        return optionTally.filter { it.inferiorTo.isEmpty() }.map { it.id } to usedMatchups
    }

    private fun createMatchups() = buildList {
        for (a in options.indices) {
            for (b in a + 1 until options.size) {
                val aVotes = submittedResponses.values.count { (it.ratings[a]?: 0) > (it.ratings[b]?: 0) }
                val bVotes = submittedResponses.values.count { (it.ratings[a]?: 0) < (it.ratings[b]?: 0) }

                add(OptionMatchup(a, b, aVotes, bVotes))
            }
        }
    }

    override suspend fun processButtonClick(event: ButtonInteractionEvent) {
        if (event.customId == "poll_submit") {
            if (event.user.idLong in submittedResponses.keys) {
                event.reply(":no_entry: Du hast auf diese Umfrage bereits geantwortet").setEphemeral(true).await()
                return
            }

            submittedResponses[event.user.idLong] = cachedResponses.getOrPut(event.user.idLong) { UserRatings(mutableMapOf()) }.copy()
            event.reply("\uD83D\uDCE9 Vielen Dank für deine Stimme!").setEphemeral(true).await()

        } else if (event.customId == "poll_end") {
            if (event.user.idLong != creator.idLong) {
                event.reply(":no_entry: Nur der Ersteller der Umfrage kann sie abbrechen").setEphemeral(true).await()
                return
            }
            conclude()
        }
    }

    override suspend fun processStringSelect(event: StringSelectInteractionEvent) {
        val userId = event.interaction.user.idLong
        val optionId = event.interaction.component.customId.split(":").last().toInt()
        val rating = event.selectedOptions.first().value.toInt()

        cachedResponses.getOrPut(userId) { UserRatings(mutableMapOf()) }.ratings[optionId] = rating
    }
}

private class Option (val id: Int) {
    val inferiorTo = mutableSetOf<Option>()
    val superiorTo = mutableSetOf<Option>()
}

private data class OptionMatchup(val firstId: Int, val secondId: Int, val firstPreferences: Int, val secondPreferences: Int) {
    val winningPercent =
        if (firstPreferences + secondPreferences == 0)
            0.5
        else
            max(firstPreferences, secondPreferences) / (firstPreferences + secondPreferences).toDouble()

    val winnerId = if (firstPreferences > secondPreferences) firstId else secondId
    val looserId = if (firstPreferences <= secondPreferences) firstId else secondId
}

suspend fun sendPollInfo(event: ButtonInteractionEvent) {
    event.reply_ {
        useComponentsV2 = true
        components += Container {
            accentColor = pollColor

            text (
                """
                    # :books: Umfrage Verfahren
                    ### Prinzip
                    Alle Optionen werden paarweise direkt verglichen.
                    Anhand der Sterne-Bewertungen wird jeweils eine Stichwahl ausgewertet.
                    Aus den Stichwahl-Ergebnissen wird das Gesamtergebnis ermittelt.
                    
                    ### Details
                    - Eindeutigere Stichwahl-Ergebnisse werden zuerst abgearbeitet
                    - Unter gleich eideutigen Ergebnissen ist die Reihenfolge zufällig
                    - Wenn ein Kreislauf (Schere-Stein-Papier Prinzip) entsteht wird das letzte (uneindeutigere) Ergebnis ignoriert
                    - Nicht-Bewerten einer Option zählt als "0 Sterne"
                """.trimIndent()
            )

            actionRow {
                linkButton("https://en.wikipedia.org/wiki/Ranked_pairs", "Wikipedia Artikel")
            }
        }
    }.setEphemeral(true).await()
}