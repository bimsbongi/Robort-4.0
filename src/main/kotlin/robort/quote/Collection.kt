package robort.quote

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import robort.QuoteMessages
import robort.mainLogger
import robort.menu.ChannelType
import robort.menu.getAllSetChannels
import kotlin.time.Duration.Companion.hours

private data class QuoteMessageData(val id: Long, val timestamp: Long, val content: String)

suspend fun scrapeQuoteMessages(quoteChannel: TextChannel) {

    val priorMessages = suspendTransaction {
        QuoteMessages.select(QuoteMessages.messageId, QuoteMessages.timestamp)
            .where { QuoteMessages.channelId eq quoteChannel.idLong }.toSet()
    }
    val newestSavedMessage = priorMessages.maxByOrNull { it[QuoteMessages.timestamp] }?.let { it[QuoteMessages.timestamp] }?: 0

    val fetchedMessages = mutableListOf<QuoteMessageData>()
    for (message in quoteChannel.iterableHistory) {
        if (message.timeCreated.toEpochSecond() <= newestSavedMessage) break

        fetchedMessages.add(QuoteMessageData(message.idLong, message.timeCreated.toEpochSecond(), message.contentRaw))
    }

    suspendTransaction {
        QuoteMessages.batchInsert(fetchedMessages, ignore = true) {
            this[QuoteMessages.messageId] = it.id
            this[QuoteMessages.timestamp] = it.timestamp
            this[QuoteMessages.channelId] = quoteChannel.idLong
            this[QuoteMessages.guildId] = quoteChannel.guild.idLong
            this[QuoteMessages.cachedContent] = it.content
        }
    }
}

suspend fun launchBackgroundQuoteDownloader() = coroutineScope {
    launch {
        backgroundQuoteDownloader()
    }
    mainLogger.info("Started background quote downloader")
}

suspend fun backgroundQuoteDownloader() {
    while (true) {
        val quoteChannels = getAllSetChannels(ChannelType.QUOTES)
        mainLogger.info("Collecting quotes from ${quoteChannels.size} channels")

        quoteChannels.forEach { channel ->
            scrapeQuoteMessages(channel)
        }

        delay(1.hours)
    }
}