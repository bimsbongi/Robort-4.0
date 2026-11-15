package robort.menu

import dev.minn.jda.ktx.generics.getChannel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toSet
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.upsert
import robort.GuildChannelSettings
import robort.jda

enum class ChannelType {
    QUOTES,
    DAILY_QUOTES,
    INVITES,
    DEFAULT;
}

private val cachedChannelSettings = mutableMapOf<Pair<Long, ChannelType>, Long?>()

suspend fun getGuildSetChannel(guildId: Long, channelType: ChannelType) =
    cachedChannelSettings.getOrPut(guildId to channelType) {
        suspendTransaction {
            GuildChannelSettings.selectAll()
                .where { (GuildChannelSettings.guildId eq guildId) and (GuildChannelSettings.channelType eq channelType) }
                .firstOrNull()?.get(GuildChannelSettings.channelId)
        }
    }?.let { jda.getChannel<TextChannel>(it) }


suspend fun saveGuildSetChannel(guildId: Long, channelType: ChannelType, channelId: Long?) = suspendTransaction {
    cachedChannelSettings[guildId to channelType] = channelId

    GuildChannelSettings.upsert {
        it[GuildChannelSettings.guildId] = guildId
        it[GuildChannelSettings.channelType] = channelType
        it[GuildChannelSettings.channelId] = channelId
    }
}

suspend fun getAllSetChannels(channelType: ChannelType): List<TextChannel> {
    val databaseResult = suspendTransaction {
        GuildChannelSettings.selectAll()
            .where { GuildChannelSettings.channelType eq channelType }
            .toSet()
    }

   return databaseResult.mapNotNull { resultRow ->
        cachedChannelSettings.getOrPut(resultRow[GuildChannelSettings.guildId] to channelType) { resultRow[GuildChannelSettings.channelId] }?.let { jda.getChannel<TextChannel>(it) }
   }
}