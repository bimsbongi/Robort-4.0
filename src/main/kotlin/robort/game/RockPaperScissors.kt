package robort.game

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.edit
import dev.minn.jda.ktx.messages.reply_
import dev.minn.jda.ktx.messages.send
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import robort.MessageActivity
import robort.jda
import robort.runningMessageActivities
import java.awt.Color
import kotlin.collections.get

suspend fun challengeRockPaperScissorsCommand(interaction: SlashCommandInteractionEvent) {
    val opponent = interaction.getOption("opponent")?.asUser?: error("No rock paper scissors opponent")

    val game = RockPaperScissorsGame(interaction.user, opponent, interaction.channel)
    game.startGameCommand(interaction)
    runningMessageActivities[game.message!!.idLong] = game
}

val rockPaperScissorsEmoji = mapOf(
    "rock" to ":rock:",
    "paper" to ":newspaper:",
    "scissors" to ":scissors:"
)

class RockPaperScissorsGame(
    val challenger: User,
    val opponent: User,
    val channel: MessageChannel
): MessageActivity {
    var message: Message? = null
    private var challengerChoice:String? = null
    private var opponentChoice:String? = null

    suspend fun startGameCommand(interaction: SlashCommandInteractionEvent) {
        message = interaction.reply_ {
            useComponentsV2 = true
            components += rockPaperScissorsContainer(true)
        }.await().retrieveOriginal().await()
    }

    suspend fun startGameRemote() {
        message = channel.send {
            useComponentsV2 = true
            components += rockPaperScissorsContainer(true)
        }.await()
    }

    private fun rockPaperScissorsContainer(active: Boolean) = Container {
        accentColor = Color(0xbc00ff)
        text("""
        ### :scissors: Schere   :rock: Stein   :newspaper: Papier
        **${challenger.effectiveName}** *vs.* **${opponent.effectiveName}**
        """.trimIndent()
        )

        actionRow {
            button(ButtonStyle.SECONDARY, "scissors", "", Emoji.fromUnicode("✂\uFE0F"), !active)
            button(ButtonStyle.SECONDARY, "rock", "", Emoji.fromUnicode("\uD83E\uDEA8"), !active)
            button(ButtonStyle.SECONDARY, "paper", "", Emoji.fromUnicode("\uD83D\uDCF0"), !active)
        }
    }

    private suspend fun conclude() {
        val resultText = if (firstWin(challengerChoice, opponentChoice)) {
            ":partying_face: **${challenger.asMention}** gewinnt mit ${rockPaperScissorsEmoji[challengerChoice]} gegen ${opponent.asMention}'s ${rockPaperScissorsEmoji[opponentChoice]}"
        } else if (firstWin(opponentChoice, challengerChoice)) {
            ":partying_face: **${opponent.asMention}** gewinnt mit ${rockPaperScissorsEmoji[opponentChoice]} gegen ${challenger.asMention}'s ${rockPaperScissorsEmoji[challengerChoice]}"
        } else {
            ":grey_exclamation: Unentschieden, ${challenger.asMention} und ${opponent.asMention} haben beide ${rockPaperScissorsEmoji[opponentChoice]} gewählt"
        }

        message!!.edit {
            useComponentsV2 = true
            components += rockPaperScissorsContainer(false)
        }.await()

        channel.sendMessage(resultText).setMessageReference(message!!.idLong).await()
    }

    override suspend fun processButtonClick(event: ButtonInteractionEvent) {
        if (event.user == challenger && challengerChoice == null) challengerChoice = event.customId
        else if (event.user == opponent && opponentChoice == null) opponentChoice = event.customId

        if (opponent.idLong == jda.selfUser.idLong) makeBotMove()


        if (challengerChoice != null && opponentChoice != null) conclude()
    }

    private fun makeBotMove() {
        opponentChoice = if (Math.random() > 0.5)
            arrayOf("rock", "paper", "scissors").random()
        else
            if (challengerChoice == "rock") "paper" else if (challengerChoice == "paper") "scissors" else "rock"
    }
}

private fun firstWin(first: String?, second: String?) =
    (first == "rock" && second == "scissors") ||
            (first == "paper" && second == "rock") ||
            (first == "scissors" && second == "paper")