package robort

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.default
import dev.minn.jda.ktx.jdabuilder.intents
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory
import robort.quiz.requestArtQuizCommand
import robort.quiz.requestBigQuestionsQuizCommand
import robort.quiz.requestComputerScienceQuizCommand
import robort.quiz.requestPoliticalCompassQuizCommand
import robort.utils.animatePanda
import robort.utils.askNaturalStupidityCommand
import robort.utils.askOracleCommand
import robort.utils.bookAuraCommand
import robort.utils.onSlashCommand
import robort.utils.showProfileColorCommand

val mainLogger = LoggerFactory.getLogger("Robort")
val jda = default(System.getenv("BOT_TOKEN") ?: "") {
    intents += GatewayIntent.GUILD_MEMBERS
}

suspend fun main() {
    registerCommands()

    registerListeners()

    registerDatabase()
}

suspend fun registerCommands() {

    val registerCommandAction = jda.updateCommands()

    registerCommandAction.addCommands(Commands.slash("panda", "Halt nen Panda"))
    jda.onSlashCommand("panda") {
        animatePanda(it)
    }

    registerCommandAction.addCommands(Commands.slash("profile-color", "Die Farbe des default-Profilbilds eines Users abfragen")
        .addOption(OptionType.USER, "user", "User", true))
    jda.onSlashCommand("profile-color") {
        showProfileColorCommand(it)
    }

    registerCommandAction.addCommands(Commands.slash("orakel", "Ein allwissendes Orakel befragen")
        .addOption(OptionType.STRING, "question", "Die Frage an das Orakel", true))
    jda.onSlashCommand("orakel") {
        askOracleCommand(it)
    }

    registerCommandAction.addCommands(Commands.slash("nd", "Den ND (Natürliche Dummheit) chatbot nutzen")
        .addOption(OptionType.STRING, "prompt", "Prompt für die natürliche Dummheit", true))
    jda.onSlashCommand("nd") {
        askNaturalStupidityCommand(it)
    }

    registerCommandAction.addCommands(Commands.slash("informatik-o-mat", "Diagnostiziert chronische Informatik und verbundene Krankheiten"))
    jda.onSlashCommand("informatik-o-mat") {
        requestComputerScienceQuizCommand(it)
    }

    registerCommandAction.addCommands(Commands.slash("bigquestions", "Die wichtigen Fragen im Leben"))
    jda.onSlashCommand("bigquestions") {
        requestBigQuestionsQuizCommand(it)
    }

    registerCommandAction.addCommands(Commands.slash("isitart", "Endlich eintscheiden was Kunst ist und was weg kann"))
    jda.onSlashCommand("isitart") {
        requestArtQuizCommand(it)
    }

    registerCommandAction.addCommands(Commands.slash("compass", "Der aller-akkurateste Polische Kompass Test"))
    jda.onSlashCommand("compass") {
        requestPoliticalCompassQuizCommand(it)
    }

    registerCommandAction.addCommands( Commands.slash("aura", "Aura von Leuten verbuchen/abrufen")
        .addOption(OptionType.USER, "user", "User", true)
        .addOption(OptionType.NUMBER, "amount", "Betrag", false)
        .addOption(OptionType.STRING, "reason", "Grund der Verbuchung", false))
    jda.onSlashCommand("aura") {
        bookAuraCommand(it)
    }

    registerCommandAction.await()
    mainLogger.info("Registered all commands")
}

private suspend fun registerDatabase() {
    setupDatabase()

    mainLogger.info("Set up Database")
}

private fun registerListeners() {
    jda.listener<ButtonInteractionEvent> { event ->
        runningActivities.forEach { (channelId, activity) ->
            if (channelId != event.interaction.channel.idLong) return@forEach

            activity.processButtonClick(event)
        }
        event.interaction.deferEdit().await()
    }

    mainLogger.info("Registered all listeners")
}
