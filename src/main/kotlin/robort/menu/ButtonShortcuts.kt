package robort.menu

import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import robort.game.challengeRockPaperScissorsButton
import robort.game.challengeTicTacToeButton
import robort.game.listenForMetaGamblingModalSubmission
import robort.game.listenForRockPaperScissorsModalSubmission
import robort.game.listenForTicTacToeModalSubmission
import robort.game.metaGamblingButton
import robort.poll.startPollButton
import robort.quiz.requestArtQuizButton
import robort.quiz.requestBigQuestionsQuizCommand
import robort.quiz.requestComputerScienceQuizButton
import robort.quiz.requestPoliticalCompassQuizButton
import robort.utils.askNaturalStupidityButton
import robort.utils.askOracleButton
import robort.utils.listenForNaturalStupidityModalSubmission
import robort.utils.listenForOracleModalSubmission

suspend fun listenForMainMenuButtonClick(event: ButtonInteractionEvent) {
    val guild = event.guild ?: return

    val defaultChannel = getGuildSetChannel(guild.idLong, ChannelType.DEFAULT)

    if (defaultChannel == null) {
        event.interaction.reply(":no_entry: Um Robort's Aktivitäten vom Menü aus zu starten muss ein default Kanal festgelegt werden").setEphemeral(true).await()
        return
    }

    when (event.customId) {
        "ttt" -> challengeTicTacToeButton(event, defaultChannel)
        "ssp" -> challengeRockPaperScissorsButton(event, defaultChannel)
        "meta-gambling" -> metaGamblingButton(event, defaultChannel)

        "compass" -> requestPoliticalCompassQuizButton(event, defaultChannel)
        "informatik-o-mat" -> requestComputerScienceQuizButton(event, defaultChannel)
        "isitart" -> requestArtQuizButton(event, defaultChannel)
        "bigquestions" -> requestBigQuestionsQuizCommand(event, defaultChannel)

        "orakel" -> askOracleButton(event, defaultChannel)
        "nd" -> askNaturalStupidityButton(event, defaultChannel)
        "poll" -> startPollButton(event, defaultChannel)
    }
}

suspend fun listenForMenuModalSubmission(event: ModalInteractionEvent) {
    listenForOracleModalSubmission(event)
    listenForNaturalStupidityModalSubmission(event)
    listenForTicTacToeModalSubmission(event)
    listenForRockPaperScissorsModalSubmission(event)
    listenForMetaGamblingModalSubmission(event)
}