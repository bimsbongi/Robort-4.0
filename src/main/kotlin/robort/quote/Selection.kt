package robort.quote

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import robort.ChosenDailyQuotes
import robort.QuoteMessages
import robort.jda
import robort.menu.ChannelType
import robort.menu.getGuildSetChannel
import kotlin.time.Clock

private const val randomQuoteCooldownPercentage = 0.5

private data class QuoteSourceMessage(val messageId: Long, val channelId: Long, val timestamp: Long)

private data class QuoteDailyPostedMessage(val messageId: Long, val timestamp: Long)

private suspend fun fetchCandidateQuoteMessages(sourceChannelId: Long) = suspendTransaction {
    QuoteMessages.selectAll()
        .where { QuoteMessages.channelIdColumn eq sourceChannelId }
        .map { QuoteSourceMessage(it[QuoteMessages.messageIdColumn], it[QuoteMessages.channelIdColumn], it[QuoteMessages.timestampColumn]) }.toSet()
}

private suspend fun fetchRecentlyPostedQuotes(guildId: Long, limit: Int) = suspendTransaction {
    ChosenDailyQuotes.select(ChosenDailyQuotes.messageIdColumn, ChosenDailyQuotes.timestampColumn)
        .where { ChosenDailyQuotes.guildIdColumn eq guildId }
        .orderBy(ChosenDailyQuotes.timestampColumn, SortOrder.DESC)
        .limit(limit)
        .map { QuoteDailyPostedMessage(it[ChosenDailyQuotes.messageIdColumn], it[ChosenDailyQuotes.timestampColumn]) }.toSet()
}

suspend fun selectDailyQuote(guildId: Long, sourceChannelId: Long): Pair<Quote, Int?>? {
    val candidates = fetchCandidateQuoteMessages(sourceChannelId)
    val recentlyPostedQuotes = fetchRecentlyPostedQuotes(guildId, (candidates.size * randomQuoteCooldownPercentage).toInt())

    return selectAnniversaryQuote(candidates, recentlyPostedQuotes)?: selectRandomQuote(candidates, recentlyPostedQuotes)?.to(null)
}

suspend fun selectGuessrQuote(guildId: Long): Quote? {
    val channel = getGuildSetChannel(guildId, ChannelType.QUOTES)?: return null
    val candidates = fetchCandidateQuoteMessages(channel.idLong)

    return selectRandomQuote(candidates, setOf())
}

private suspend fun selectAnniversaryQuote(candidates: Set<QuoteSourceMessage>, recentlyPostedQuotes: Set<QuoteDailyPostedMessage>): Pair<Quote, Int>? {
    val recentlyPostedIds = recentlyPostedQuotes.map { it.messageId }
    val currentDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    return candidates.mapNotNull {
        val quoteDate = LocalDate.fromEpochDays(it.timestamp / (60 * 60 * 24))
        val age = currentDate.year - quoteDate.year
        if (quoteDate.day != currentDate.day || quoteDate.month != currentDate.month || it.messageId in recentlyPostedIds || age == 0) return@mapNotNull null

        it to age
    }.shuffled().sortedByDescending { it.second }.firstNotNullOfOrNull {
        jda.getChannel<GuildMessageChannel>(it.first.channelId)?.retrieveMessageById(it.first.messageId)?.await()?.parseQuote()?.to(it.second)
    }
}

private suspend fun selectRandomQuote(candidates: Set<QuoteSourceMessage>, recentlyPostedQuotes: Set<QuoteDailyPostedMessage>): Quote? {
    val recentlyPostedIds = recentlyPostedQuotes.map { it.messageId }
    val newCandidates = candidates.filter { candidate -> candidate.messageId !in recentlyPostedIds }

    return newCandidates.shuffled().firstNotNullOfOrNull {
        jda.getChannel<GuildMessageChannel>(it.channelId)?.retrieveMessageById(it.messageId)?.await()?.parseQuote()
    }
}

private fun Message.parseQuote() = parseQuoteContent(this.contentRaw)

private fun parseQuoteContent(content: String): Quote? {
    val parts = sanitizeQuotes(content).split('"')
    if (parts.size % 2 == 0 || parts.size == 1) return null

    return if (parts.first() == "") {
        Quote(parts.subList(1, parts.size).chunked(2).map { it[0] to sanitizeQuoteAuthor(it[1]) })
    } else {
        Quote(parts.subList(0, parts.size - 1).chunked(2).map { it[1] to sanitizeQuoteAuthor(it[0]) })
    }
}

private fun sanitizeQuoteAuthor(content: String) =
    content.replace("-", "")
        .replace(".", "")
        .replace("~", "")
        .replace(":", "")
        .trim()

private fun sanitizeQuotes(content: String) = buildString {
    var lowerQuoteCount = 0
    content.toCharArray().forEach {
        when (it) {
            '„' -> {
                if (lowerQuoteCount > 0) append("'")
                lowerQuoteCount += 1
            }
            '“' -> {
                if (lowerQuoteCount > 0) append("'")
                lowerQuoteCount -= 1
            }
            '\n', '\t' -> {
                append(" ")
            }
            else -> append(it)
        }
    }
}.trim()
