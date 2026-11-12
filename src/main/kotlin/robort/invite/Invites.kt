package robort.invite

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.edit
import dev.minn.jda.ktx.messages.send
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import robort.MessageActivity
import robort.jda
import robort.menu.ChannelType
import robort.menu.getGuildSetChannel
import robort.runningMessageActivities
import java.awt.Color
import kotlin.collections.set

suspend fun listenForInvites(event: MessageReceivedEvent) {
    val inviteChannel = getGuildSetChannel(event.guild.idLong, ChannelType.INVITES)

    if (event.channel != inviteChannel || event.author.isBot || event.message.referencedMessage != null) return

    val invite = Invite(event.author, event.channel, event.message)
    invite.sendInvite()
    runningMessageActivities[invite.message!!.idLong] = invite
}

private sealed interface InviteResponse

enum class SimpleResponse(val content: String): InviteResponse {
    AVAILABLE("✅ Mache mit"),
    CANCELED("❌ Mache nicht mit"),
    SOON("⏳ Komme bald dazu"),
    LATER("⏰ Komme erst später"),
    UNSURE("\uD83E\uDD37 Keine Ahnung");

    override fun toString() = content
}

object EasterEggResponse: InviteResponse {
    override fun toString() = ":robot: Ich bin auch dabei! *beep, boop*"
}

class CustomResponse(val content: String): InviteResponse {
    override fun toString() = ":speech_left: $content"
}

class WaitingResponse(val targetUser: User): InviteResponse {
    override fun toString() = ":stopwatch: Warte auf ${targetUser.effectiveName}"
}

private val buttonResponseMapping = mapOf(
    "available" to SimpleResponse.AVAILABLE,
    "cancel" to SimpleResponse.CANCELED,
    "soon" to SimpleResponse.SOON,
    "later" to SimpleResponse.LATER,
    "unsure" to SimpleResponse.UNSURE
)

class Invite (
    val organizer: User,
    val channel: MessageChannel,
    val originalMessage: Message
): MessageActivity {
    var message: Message? = null
    private val responses = mutableMapOf<Long, InviteResponse?>()
    private val participants = mutableListOf<User>()

    suspend fun sendInvite() {
        originalMessage.delete().await()

        participants.add(organizer)
        participants.addAll(originalMessage.mentions.users)
        participants.addAll(
                originalMessage.mentions.roles.flatMap { role ->
                    originalMessage.guild.findMembers { it.roles.contains(role) }.await()
                }.map { it.user }
        )

        responses.putAll(participants.map { it.idLong to null })
        responses[organizer.idLong] = SimpleResponse.AVAILABLE

        if (jda.selfUser.idLong in responses) responses[jda.selfUser.idLong] = EasterEggResponse

        message = channel.send {
            useComponentsV2 = true
            components += inviteContainer()
        }.await()
    }


    fun inviteContainer() = Container {
        accentColor = Color(0x77b354)

        text(
            """
                **${organizer.effectiveName}**
                # :loudspeaker: Einladung
                ⠀
                ${originalMessage.contentRaw}
            """.trimIndent()
        )
        separator()
        text(
            participants.joinToString("\n") {
                "### ${it.effectiveName}\n${responses[it.idLong]?.toString() ?: ":question: Unbekannt"}"
            }
        )
        separator()

        actionRow {
            button(ButtonStyle.SECONDARY, "available", "", Emoji.fromUnicode("✅"))
            button(ButtonStyle.SECONDARY, "cancel", "", Emoji.fromUnicode("❌"))
            button(ButtonStyle.SECONDARY, "soon", "", Emoji.fromUnicode("⏳"))
            button(ButtonStyle.SECONDARY, "later", "", Emoji.fromUnicode("⏰"))
            button(ButtonStyle.SECONDARY, "unsure", "", Emoji.fromUnicode("\uD83E\uDD37"))
        }
        actionRow {
            entitySelectMenu(
                "waiting_for_user",
                listOf(EntitySelectMenu.SelectTarget.USER),
                valueRange = 0..1,
                placeholder = "Warte auf ..."
            )
        }
        actionRow {
            entitySelectMenu(
                "reminding_user",
                listOf(EntitySelectMenu.SelectTarget.USER),
                valueRange = 0..1,
                placeholder = "Sende Erinnerung an ..."
            )
        }
    }

    private suspend fun sendWaitingReminders(target: User, response: SimpleResponse) {
        val waiters = participants.filter { isWaitingIndirectly(it, target) }
        if (waiters.isEmpty()) return

        val waiterMentions = waiters.joinToString(" ") { it.asMention }

        if (response == SimpleResponse.AVAILABLE)
            channel.sendMessage(":rotating_light: $waiterMentions **${target.effectiveName}** ist jetzt da!").setMessageReference(message).await()
        else if (response == SimpleResponse.CANCELED)
            channel.sendMessage(":confused:  $waiterMentions **${target.effectiveName}** hat leider abgesagt.").setMessageReference(message).await()
    }

    override suspend fun processButtonClick(event: ButtonInteractionEvent) {
        if (event.user.idLong !in responses) return

        val response = buttonResponseMapping[event.customId]
        if (response == SimpleResponse.AVAILABLE || response == SimpleResponse.CANCELED) sendWaitingReminders(event.user, response)

        responses[event.user.idLong] = response

        message?.edit {
            useComponentsV2 = true
            components += inviteContainer()
        }?.await()
    }

    override suspend fun processMessageReply(event: MessageReceivedEvent) {
        if (event.author.idLong !in responses || event.author.isBot) return

        event.message.delete().await()
        responses[event.author.idLong] = CustomResponse(event.message.contentRaw)

        message?.edit {
            useComponentsV2 = true
            components += inviteContainer()
        }?.await()
    }

    override suspend fun processEntitySelect(event: EntitySelectInteractionEvent) {
        if (event.user.idLong !in responses) return
        val selectedUser = event.values.firstOrNull()?.idLong?.let { jda.retrieveUserById(it).await() }

        if (event.customId == "waiting_for_user") {
            handleWaitingSelection(selectedUser, event)
        } else if (event.customId == "reminding_user") {
            handleReminderSelection(selectedUser, event)
        }
    }

    private fun isWaitingIndirectly(waiter: User, target: User): Boolean {
        var currentUserId = waiter.idLong
        val checkedUsers = mutableSetOf<Long>()
        do {
            if (responses[currentUserId] !is WaitingResponse) return false

            val waitingTarget = (responses[currentUserId] as WaitingResponse).targetUser
            if (waitingTarget.idLong in checkedUsers) return false

            checkedUsers.add(currentUserId)
            currentUserId = waitingTarget.idLong
        } while (currentUserId != target.idLong)

        return true
    }

    private suspend fun handleReminderSelection(
        selectedUser: User?,
        event: EntitySelectInteractionEvent
    ) {
        if (selectedUser == null) return

        if (selectedUser.idLong !in responses) {
            event.interaction.reply(":no_entry: Du kannst nur Teilnehmer dieser Einladung erinnern").setEphemeral(true).await()
            return
        }

        channel.sendMessage(":bell: ${selectedUser.asMention} **${event.user.effectiveName}** schickt dir eine Erinnerung!").setMessageReference(message).await()
    }

    private suspend fun handleWaitingSelection(
        selectedUser: User?,
        event: EntitySelectInteractionEvent
    ) {
        if (selectedUser == null) responses[event.user.idLong] = null
        else {
            if (selectedUser.idLong !in responses) {
                event.interaction.reply(":no_entry: Du kannst nur auf Teilnehmer dieser Einladung warten").setEphemeral(true).await()
                return
            }

            if (responses[selectedUser.idLong] == SimpleResponse.AVAILABLE) {
                event.interaction.reply(":no_entry: ${selectedUser.effectiveName} hat bereits zugesagt")
                    .setEphemeral(true).await()
                return
            }

            if (responses[selectedUser.idLong] == SimpleResponse.CANCELED) {
                event.interaction.reply(":no_entry: ${selectedUser.effectiveName} hat bereits abgesagt")
                    .setEphemeral(true).await()
                return
            }

            if (isWaitingIndirectly(selectedUser, event.user)) {
                event.interaction.reply(":no_entry: ${selectedUser.effectiveName} wartet bereits (indirekt) auf dich")
                    .setEphemeral(true).await()
                return
            }

            responses[event.user.idLong] = WaitingResponse(selectedUser)
        }

        message?.edit {
            useComponentsV2 = true
            components += inviteContainer()
        }?.await()
    }
}