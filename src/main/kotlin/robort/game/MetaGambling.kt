package robort.game

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.Label
import dev.minn.jda.ktx.interactions.components.Modal
import dev.minn.jda.ktx.interactions.components.TextDisplay
import dev.minn.jda.ktx.interactions.components.TextInput
import dev.minn.jda.ktx.messages.reply_
import dev.minn.jda.ktx.messages.send
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import net.dv8tion.jda.api.components.textinput.TextInputStyle
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.upsert
import org.jetbrains.exposed.v1.r2dbc.upsertReturning
import robort.Money
import robort.jda
import robort.utils.envOrDefault
import robort.utils.loadResourceFile
import java.awt.Color
import kotlin.time.Duration.Companion.minutes

@Serializable
private data class CasinoData (
    val ticker: String,
    val name: String
)

@Serializable
private data class FinnhubQuote (
    @SerialName("c")
    val currentPrice: Double,
)

private val casinosFile = loadResourceFile("/casinos.json")?: error("Could not load casinos.json")
private val casinos = Json.decodeFromString<List<CasinoData>>(casinosFile.readText())

private val finnhubToken = envOrDefault("FINNHUB_TOKEN", "")

private val containerColor = Color(0xffbb00)
private const val roundDuration = 10
private const val startCapital = 100

private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

private suspend fun getCurrentPrice(ticker: String): Double {
    val quote = httpClient.get("https://finnhub.io/api/v1/quote?symbol=$ticker&token=$finnhubToken").body<FinnhubQuote>()

    return quote.currentPrice
}

private suspend fun placeBet(user: User, amount: Long, ticker: String, direction: String): Pair<Container, Double> {
    val money = suspendTransaction {
        Money.upsertReturning(
            onUpdate = { it[Money.money] = Money.money + (-amount) },
            body = {
                it[Money.userId] = user.idLong
                it[Money.money] = startCapital - amount
            }

        ).first()[Money.money]
    }

    val betEvaluation = getCurrentPrice(ticker)

    return Container {
        accentColor = containerColor

        text("""
            ## :game_die: Meta-Gambling
            
            ${user.effectiveName} setzt **${amount} Geld** auf ${direction}e Kurse für
            **$ticker** - *${betEvaluation}*
            
            Übriges Kapital: **$money Geld**
            Runde endet <t:${System.currentTimeMillis() / 1000 + roundDuration * 60}:R>
        """.trimIndent())
    } to betEvaluation
}

suspend fun resolveBet(betMessage: Message, user: User, betEvaluation: Double, amount: Long, ticker: String, direction: String) {
    val newEvaluation = getCurrentPrice(ticker)

    if (betEvaluation != newEvaluation && ((betEvaluation > newEvaluation) == (direction == "steigend"))) {
        suspendTransaction {
            Money.upsert(
                onUpdate = { it[Money.money] = Money.money + 2 * amount },
                body = {
                    it[Money.userId] = user.idLong
                    it[Money.money] = startCapital + 2 * amount
                }
            )
        }
        betMessage.channel.sendMessage(":dollar: :green_circle: :dollar: **${ticker}** geht auf $newEvaluation! ${user.asMention} gewinnt **${2 * amount} Geld**! :dollar: :green_circle: :dollar:")
            .setMessageReference(betMessage).await()
    } else {
        betMessage.channel.sendMessage(":money_with_wings: **${ticker}** geht auf $newEvaluation ${user.asMention} verliert diese Runde.")
            .setMessageReference(betMessage).await()
    }
}

private suspend fun infoContainer(user: User): Container {
    val userMoney = suspendTransaction {
        Money.selectAll().where { Money.userId eq user.idLong }.map { it[Money.money] }.firstOrNull()?: startCapital
    }

    return Container {
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
        text("### :dollar::green_circle::dollar: -=- -=- -=- -=- -=- :dollar::green_circle::dollar:")
        separator()
        text("${user.effectiveName} hat **$userMoney Geld**")
    }
}

suspend fun metaGamblingButton(event: ButtonInteractionEvent, targetChannel: MessageChannel) {
    event.replyModal(
        Modal(title = "Meta-Gambling", id = "meta_gambling:${targetChannel.idLong}") {
            components += TextDisplay("Nichts eintragen um aktuelle Kurse anzuzeigen.\n\nAuf ein Casino setzen:")
            components += Label (
                label = "Ticker",
                child = TextInput(
                    style = TextInputStyle.SHORT,
                    customId = "ticker",
                    required = false
                )
            )
            components += Label (
                label = "Einsatz",
                child = TextInput(
                    style = TextInputStyle.SHORT,
                    customId = "amount",
                    required = false
                )
            )

            components += Label (
                label = "Richtung",
                child = StringSelectMenu.create("direction")
                    .setRequired(false)
                    .addOption("steigend", "steigend")
                    .addOption("fallend", "fallend")
                    .build()
            )
        }
    ).await()
}

suspend fun listenForMetaGamblingModalSubmission(event: ModalInteractionEvent) {
    if (!event.interaction.modalId.startsWith("meta_gambling")) return

    val ticker = event.getValue("ticker")?.asString?: ""
    val amountRaw = event.getValue("amount")?.asString?: ""
    val direction = event.getValue("direction")?.asStringList?.firstOrNull()
    val channel = jda.getChannel<MessageChannel>(event.interaction.modalId.split(":").last())?: error("Cannot find channel for meta-gambling")

    if (ticker == "" && amountRaw == "" && direction == null) {
        event.deferEdit().await()
        channel.send {
            useComponentsV2 = true
            components += infoContainer(event.user)
        }.await()
        return
    }

    if (ticker != "" && amountRaw != "" && direction != null) {
        val amount = amountRaw.toLongOrNull()
        if (amount == null || amount <= 0) {
            event.interaction.reply(":no_entry: Einstz muss eine positive Zahl sein").setEphemeral(true).await()
            return
        }

        if (ticker !in casinos.map { it.ticker }) {
            event.interaction.reply(":no_entry: Invalides ticker symbol")
                .setEphemeral(true).await()
            return
        }

        event.deferEdit().await()
        placeBetRemote(ticker, channel, event.user, amount, direction)
        return
    }

    event.interaction.reply(":no_entry: Gebe entweder alle Parameter an, um zu setzten oder keine um die aktuellen Kurse abzufragen.")
        .setEphemeral(true).await()
}

suspend fun metaGamblingCommand(interaction: SlashCommandInteractionEvent) {
    val ticker = interaction.getOption("ticker")?.asString
    val amount = interaction.getOption("amount")?.asDouble?.toLong()
    val direction = interaction.getOption("direction")?.asString

    if (ticker == null && amount == null && direction == null) {
        interaction.reply_ {
            useComponentsV2 = true
            components += infoContainer(interaction.user)
        }.await()
        return
    }

    if (ticker != null && amount != null && direction != null) {
        if (amount <= 0) {
            interaction.reply(":no_entry: Einstz muss eine positive Zahl sein").setEphemeral(true).await()
            return
        }

        if (ticker !in casinos.map { it.ticker }) {
            interaction.reply(":no_entry: Invalides ticker symbol")
                .setEphemeral(true).await()
            return
        }

        placeBetCommand(ticker, interaction, amount, direction)
        return
    }

    interaction.reply(":no_entry: Gebe entweder alle Parameter an, um zu setzten oder keine um die aktuellen Kurse abzufragen.")
        .setEphemeral(true).await()
}

private suspend fun placeBetCommand(
    ticker: String,
    interaction: SlashCommandInteractionEvent,
    amount: Long,
    direction: String
) {
    val (betContainer, betEvaluation) = placeBet(interaction.user, amount, ticker, direction)
    val message = interaction.reply_ {
        useComponentsV2 = true
        components += betContainer
    }.await().retrieveOriginal().await()

    delay(roundDuration.minutes)
    resolveBet(message, interaction.user, betEvaluation, amount, ticker, direction)
}

private suspend fun placeBetRemote(
    ticker: String,
    targetChannel: MessageChannel,
    user: User,
    amount: Long,
    direction: String
) {
    val (betContainer, betEvaluation) = placeBet(user, amount, ticker, direction)
    val message = targetChannel.send {
        useComponentsV2 = true
        components += betContainer
    }.await()

    delay(roundDuration.minutes)
    resolveBet(message, user, betEvaluation, amount, ticker, direction)
}
