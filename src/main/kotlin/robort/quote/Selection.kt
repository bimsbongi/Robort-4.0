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
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import robort.ChosenDailyQuotes
import robort.QuoteMessages
import robort.jda
import java.time.LocalDateTime
import kotlin.time.Clock

private val randomQuoteCooldownPercentage = 0.5

private data class QuoteSourceMessage(val messageId: Long, val channelId: Long, val timestamp: Long)

private data class QuoteDailyPostedMessage(val messageId: Long, val channelId: Long, val timestamp: Long)

private suspend fun fetchCandidateQuoteMessages(guildId: Long) = suspendTransaction {
    QuoteMessages.select(QuoteMessages.messageId)
        .where { QuoteMessages.guildId eq guildId }
        .map { QuoteSourceMessage(it[QuoteMessages.messageId], it[QuoteMessages.channelId], it[QuoteMessages.timestamp]) }.toSet()
}

private suspend fun fetchRecentlyPostedQuotes(guildId: Long, limit: Int) = suspendTransaction {
    ChosenDailyQuotes.select(QuoteMessages.messageId)
        .where { QuoteMessages.guildId eq guildId }
        .orderBy(QuoteMessages.timestamp, SortOrder.DESC)
        .limit(limit)
        .map { QuoteDailyPostedMessage(it[QuoteMessages.messageId], it[QuoteMessages.channelId], it[QuoteMessages.timestamp]) }.toSet()
}

suspend fun selectDailyQuote(guildId: Long): Pair<Message, Int?>? {
    val candidates = fetchCandidateQuoteMessages(guildId)
    val recentlyPostedQuotes = fetchRecentlyPostedQuotes(guildId, (candidates.size * randomQuoteCooldownPercentage).toInt())

    return selectAnniversaryQuote(candidates, recentlyPostedQuotes)?: selectRandomDailyQuote(candidates, recentlyPostedQuotes)?.to(null)
}

private suspend fun selectAnniversaryQuote(candidates: Set<QuoteSourceMessage>, recentlyPostedQuotes: Set<QuoteDailyPostedMessage>): Pair<Message, Int>? {
    val recentlyPostedIds = recentlyPostedQuotes.map { it.messageId }
    val currentDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    return candidates.mapNotNull {
        val quoteDate = LocalDate.fromEpochDays(it.timestamp / (60 * 60 * 24))
        val age = currentDate.year - quoteDate.year
        if (quoteDate.day != currentDate.day || quoteDate.month != currentDate.month || it.messageId in recentlyPostedIds || age == 0) return@mapNotNull null

        it to age
    }.shuffled().sortedByDescending { it.second }.firstNotNullOfOrNull {
        jda.getChannel<GuildMessageChannel>(it.first.channelId)?.retrieveMessageById(it.first.messageId)?.await()?.to(it.second)
    }
}

private suspend fun selectRandomDailyQuote(candidates: Set<QuoteSourceMessage>, recentlyPostedQuotes: Set<QuoteDailyPostedMessage>): Message? {
    val recentlyPostedIds = recentlyPostedQuotes.map { it.messageId }
    val newCandidates = candidates.filter { candidate -> candidate.messageId !in recentlyPostedIds }

    return newCandidates.shuffled().firstNotNullOfOrNull {
        jda.getChannel<GuildMessageChannel>(it.channelId)?.retrieveMessageById(it.messageId)?.await()
    }
}

