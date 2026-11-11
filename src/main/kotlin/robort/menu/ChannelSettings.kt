package robort.menu

import dev.minn.jda.ktx.generics.getChannel
import kotlinx.coroutines.flow.firstOrNull
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
    }?.let { jda.getChannel(it) }


suspend fun saveGuildSetChannel(guildId: Long, channelType: ChannelType, channelId: Long?) = suspendTransaction {
    cachedChannelSettings[guildId to channelType] = channelId

    GuildChannelSettings.upsert {
        it[GuildChannelSettings.guildId] = guildId
        it[GuildChannelSettings.channelType] = channelType
        it[GuildChannelSettings.channelId] = channelId
    }
}