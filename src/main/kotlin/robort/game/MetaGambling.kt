package robort.game

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.upsertReturning
import robort.Aura
import robort.AuraTransactions
import robort.Money
import robort.utils.loadResourceFile
import java.awt.Color

@Serializable
private data class CasinoData (
    val ticker: String,
    val name: String
)

private val casinosFile = loadResourceFile("/casinos.json")?: error("Could not load casinos.json")
private val casinos = Json.decodeFromString<List<CasinoData>>(casinosFile.readText())

private val containerColor = Color(0xffbb00)

private fun getCurrentPrice(ticker: String): Double {
    // TODO
    return (Math.random() * 1000).toInt().toDouble()
}

private suspend fun placeBet(user: User, amount: Long, ticker: String, direction: String): Pair<Container, Double> {
    val money = suspendTransaction {
        Money.upsertReturning(
            onUpdate = { it[Money.money] = Money.money + (-amount) },
            body = {
                it[Money.userId] = user.idLong
                it[Money.money] = amount
            }

        ).first()[Money.money]
    }

    val betEvaluation = getCurrentPrice(ticker)

    return Container {
        accentColor = containerColor

        text("""
            ## :game_die: Meta-Gambling
            
            ${user.effectiveName} setzt **${amount} Geld** auf \n **$ticker** - *$betEvaluation
            
        """.trimIndent())
    } to betEvaluation
}

private fun showCurrentPricesContainer() = Container {
    accentColor = containerColor
    val prices = casinos.associateWith { getCurrentPrice(it.ticker) }
    text(
        """
            ## :game_die: Meta-Gambling
            
            ### :star::diamonds::star: Fabulous Casinos :star::diamonds::star:
        """.trimIndent()
    )
    separator()
    text(
        prices.map {
            "**${it.key.ticker}** - *${it.value}*\n${it.key.name}\n"
        }.joinToString("\n")
    )
    separator()
    text("""### :dollar::green_circle::dollar: -=- -=- -=- -=- -=- :dollar::green_circle::dollar:""")
}

suspend fun metaGamblingCommand(interaction: SlashCommandInteractionEvent) {
    val ticker = interaction.getOption("ticker")?.asString
    val amount = interaction.getOption("amount")?.asDouble?.toLong()
    val direction = interaction.getOption("direction")?.asString

    if (ticker == null && amount == null && direction == null) {
        interaction.reply_ {
            useComponentsV2 = true
            components += showCurrentPricesContainer()
        }.await()

    } else if (ticker != null && amount != null && direction != null) {

    } else {
        interaction.reply(":no_entry: Gebe entweder alle Parameter an, um zu setzten oder keine um die aktuellen Kurse abzufragen.")
            .setEphemeral(true).await()
    }
}