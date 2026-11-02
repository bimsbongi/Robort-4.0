package robort

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.jdabuilder.default
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.slf4j.LoggerFactory
import robort.utils.animatePanda
import robort.utils.askNaturalStupidityCommand
import robort.utils.askOracleCommand
import robort.utils.onSlashCommand
import robort.utils.showProfileColorCommand

val mainLogger = LoggerFactory.getLogger("Robort")

suspend fun main() {
    val jda = default(System.getenv("BOT_TOKEN") ?: "")

    registerCommands(jda)
}

suspend fun registerCommands(jda: JDA) {

    jda.upsertCommand("panda", "Halt nen Panda").await()
    jda.onSlashCommand("panda") {
        animatePanda(it)
    }

    jda.upsertCommand("profile-color", "Die Farbe des default-Profilbilds eines Users abfragen")
        .addOption(OptionType.USER, "user", "User", true).await()
    jda.onSlashCommand("profile-color") {
        showProfileColorCommand(it)
    }

    jda.upsertCommand("orakel", "Ein allwissendes Orakel befragen")
        .addOption(OptionType.STRING, "question", "Die Frage an das Orakel", true).await()
    jda.onSlashCommand("orakel") {
        askOracleCommand(it)
    }

    jda.upsertCommand("nd", "Den ND (Natürliche Dummheit) chatbot nutzen")
        .addOption(OptionType.STRING, "prompt", "Prompt für die natürliche Dummheit", true).await()
    jda.onSlashCommand("nd") {
        askNaturalStupidityCommand(it)
    }

    mainLogger.info("Registered all commands")
}
