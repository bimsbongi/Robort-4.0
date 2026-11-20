package robort.quote

import dev.minn.jda.ktx.coroutines.asFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import robort.QuoteMessages
import robort.mainLogger
import robort.menu.ChannelType
import robort.menu.getAllSetChannels
import kotlin.time.Duration.Companion.hours


private val scrapeScope = CoroutineScope(Dispatchers.Default)

private suspend fun scrapeQuoteMessages(quoteChannel: GuildMessageChannel) {
    val latestMessageId = quoteChannel.latestMessageIdLong

    val lastKnownMessageId = suspendTransaction {
        val max = QuoteMessages.messageIdColumn.max()
        QuoteMessages.select(max).where { QuoteMessages.channelIdColumn eq quoteChannel.idLong }.firstOrNull()?.get(max)
    }
    if (lastKnownMessageId == latestMessageId) return

    val fetchedMessages = quoteChannel.iterableHistory.asFlow().takeWhile {
        it.idLong > (lastKnownMessageId?: 0)
    }.toList()

    suspendTransaction {
        QuoteMessages.batchInsert(fetchedMessages, ignore = true) {
            this[QuoteMessages.messageIdColumn] = it.idLong
            this[QuoteMessages.timestampColumn] = it.timeCreated.toEpochSecond()
            this[QuoteMessages.channelIdColumn] = quoteChannel.idLong
            this[QuoteMessages.guildIdColumn] = quoteChannel.guild.idLong
            this[QuoteMessages.cachedContentColumn] = it.contentRaw
        }
    }
}

fun launchBackgroundQuoteDownloader() {
    scrapeScope.launch {
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

        delay(12.hours)
    }
}

data class Quote(
    val content: List<Pair<String, String>>
)