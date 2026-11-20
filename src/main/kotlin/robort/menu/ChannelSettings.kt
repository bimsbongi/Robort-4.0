package robort.menu

import dev.minn.jda.ktx.generics.getChannel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toSet
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
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
                .where { (GuildChannelSettings.guildIdColumn eq guildId) and (GuildChannelSettings.channelTypeColumn eq channelType) }
                .firstOrNull()?.get(GuildChannelSettings.channelIdColumn)
        }
    }?.let { jda.getChannel<GuildMessageChannel>(it) }


suspend fun saveGuildSetChannel(guildId: Long, channelType: ChannelType, channelId: Long?) = suspendTransaction {
    cachedChannelSettings[guildId to channelType] = channelId

    GuildChannelSettings.upsert {
        it[GuildChannelSettings.guildIdColumn] = guildId
        it[GuildChannelSettings.channelTypeColumn] = channelType
        it[GuildChannelSettings.channelIdColumn] = channelId
    }
}

suspend fun getAllSetChannels(channelType: ChannelType): List<GuildMessageChannel> {
    val databaseResult = suspendTransaction {
        GuildChannelSettings.selectAll()
            .where { GuildChannelSettings.channelTypeColumn eq channelType }
            .toSet()
    }

   return databaseResult.mapNotNull { resultRow ->
        cachedChannelSettings.getOrPut(resultRow[GuildChannelSettings.guildIdColumn] to channelType) {
            resultRow[GuildChannelSettings.channelIdColumn]
        }?.let { jda.getChannel<GuildMessageChannel>(it) }
   }
}