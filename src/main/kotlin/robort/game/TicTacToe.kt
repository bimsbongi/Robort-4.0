package robort.game

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.InlineActionRow
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

suspend fun challengeTicTacToeCommand(interaction: SlashCommandInteractionEvent) {
    val opponent = interaction.getOption("opponent")?.asUser?: error("No rock paper scissors opponent")

    val game = TicTacToeGame(interaction.user, opponent, interaction.channel)
    game.startGameCommand(interaction)
    runningMessageActivities[game.message!!.idLong] = game
}

private val winLines = arrayOf(
    arrayOf(0, 1, 2),
    arrayOf(3, 4, 5),
    arrayOf(6, 7, 8),
    arrayOf(0, 3, 6),
    arrayOf(1, 4, 7),
    arrayOf(2, 5, 8),
    arrayOf(0, 4, 8),
    arrayOf(2, 4, 6)
)

class TicTacToeGame(
    val challenger: User,
    val opponent: User,
    val channel: MessageChannel
): MessageActivity {
    enum class CellState(
        val buttonStyle: ButtonStyle,
        val occupied: Boolean,
        val label: String,
        val emoji: Emoji? = null
    ) {
        EMPTY(ButtonStyle.SECONDARY, false, "⠀"),
        CHALLENGER_PIECE(ButtonStyle.PRIMARY, true, "", Emoji.fromUnicode("\uD83D\uDD37")),
        OPPONENT_PIECE(ButtonStyle.DANGER, true, "", Emoji.fromUnicode("\uD83D\uDD34"));
    }

    var message: Message? = null
    val boardState = MutableList(9) { CellState.EMPTY }
    var turn = 0
    val activePlayer get() = if (turn % 2 == 0) challenger else opponent

    suspend fun startGameCommand(interaction: SlashCommandInteractionEvent) {
        message = interaction.reply_ {
            useComponentsV2 = true
            components += ticTacToeContainer(true)
        }.await().retrieveOriginal().await()
    }

    suspend fun startGameRemote() {
        message = channel.send {
            useComponentsV2 = true
            components += ticTacToeContainer(true)
        }.await()
    }

    private fun ticTacToeContainer(active: Boolean) = Container {
        accentColor = Color(0xff5f05)
        text("""
        ### :large_orange_diamond: Tick-Tack-Toe
        **${challenger.effectiveName}** :large_blue_diamond: *vs.* :red_circle: **${opponent.effectiveName}**
        
        **${activePlayer.effectiveName}** ist am Zug
        """.trimIndent()
        )

        fun InlineActionRow.boardButton(idx: Int) {
            val state = boardState[idx]
            button(state.buttonStyle, idx.toString(), state.label, state.emoji, !active || state.occupied)
        }

        actionRow {
            boardButton(0)
            boardButton(1)
            boardButton(2)
        }
        actionRow {
            boardButton(3)
            boardButton(4)
            boardButton(5)
        }
        actionRow {
            boardButton(6)
            boardButton(7)
            boardButton(8)
        }
    }

    private suspend fun conclude(winner: User?) {
        val resultText = if (winner != null) ":partying_face: **${winner.asMention}** gewinnt!" else ":grey_exclamation: Unentschieden"

        message!!.edit {
            useComponentsV2 = true
            components += ticTacToeContainer(false)
        }.await()

        channel.sendMessage(resultText).setMessageReference(message!!.idLong).await()
    }

    private fun winningStateFor(pieceType: CellState) =
        winLines.any { winLine -> winLine.all { cellIdx -> boardState[cellIdx] == pieceType } }

    private fun criticalCellsFor(pieceType: CellState) =
        winLines.mapNotNull { winLine ->
            if ( winLine.count { cellIdx -> boardState[cellIdx] == pieceType } < 2) null
            else winLine.firstOrNull { cellIdx -> !boardState[cellIdx].occupied }
        }

    private suspend fun makeBotMove() {
        val winMoves = criticalCellsFor(CellState.OPPONENT_PIECE)
        val blockMoves = criticalCellsFor(CellState.CHALLENGER_PIECE)
        val randomMoves = (0 until 9).filter { !boardState[it].occupied }.shuffled()

        val movePriority = winMoves + blockMoves + randomMoves

        boardState[movePriority.first()] = CellState.OPPONENT_PIECE
        advanceGame()
    }

    override suspend fun processButtonClick(event: ButtonInteractionEvent) {
        if (event.user.idLong != activePlayer.idLong) return

        val pieceType = if (turn % 2 == 0) CellState.CHALLENGER_PIECE else CellState.OPPONENT_PIECE

        boardState[event.customId.toInt()] = pieceType
        advanceGame()
    }

    private suspend fun advanceGame() {
        turn += 1
        message!!.edit {
            useComponentsV2 = true
            components += ticTacToeContainer(true)
        }.await()

        if (boardState.all { it.occupied }) {
            conclude(null)
        } else if (winningStateFor(CellState.CHALLENGER_PIECE)) {
            conclude(challenger)
        } else if (winningStateFor(CellState.OPPONENT_PIECE)) {
            conclude(opponent)
        } else {
            if (activePlayer.idLong == jda.selfUser.idLong) makeBotMove()
        }
    }
}