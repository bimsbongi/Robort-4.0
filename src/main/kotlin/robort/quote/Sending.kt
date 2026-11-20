package robort.quote

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.send
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.utils.FileUpload
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import robort.ChosenDailyQuotes
import robort.jda
import robort.mainLogger
import robort.menu.ChannelType
import robort.menu.getAllSetChannels
import java.awt.Color
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.time.Clock

private val anniversaryColors = mapOf(
    1 to Color(0xce8946),
    2 to Color(0x848482),
    3 to Color(0xd99100),
    4 to Color(0x73daff),
    5 to Color(0xcf71ff),
)
private val defaultColor = Color.WHITE


suspend fun sendOutDailyQuotes() {
    mainLogger.info("Sending out daily quotes")

    val sourceChannels = getAllSetChannels(ChannelType.QUOTES).associate { it.guild.idLong to it.idLong }
    val postingChannels = getAllSetChannels(ChannelType.DAILY_QUOTES).associate { it.guild.idLong to it.idLong }

    val eligibleGuilds = sourceChannels.keys.intersect(postingChannels.keys)
    val dateSeed = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()

    eligibleGuilds.forEach { guildId ->
        val (quote, anniversary) = selectDailyQuote(guildId, sourceChannels[guildId]!!)?: return@forEach
        val image = generateQuoteImage(quote, guildId, dateSeed)
        val postChannel = jda.getChannel<GuildMessageChannel>(postingChannels[guildId]!!)?: error("Failed to fetch posting channel")

        val bytes = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", bytes)
        bytes.flush()
        val fileUpload = FileUpload.fromData(bytes.toByteArray(), "image.jpg")

        val message = if (anniversary == null) {
            postChannel.sendFiles(fileUpload).await()
        } else {
            postChannel.send {
                useComponentsV2 = true
                components += Container {
                    accentColor = anniversaryColors[anniversary] ?: defaultColor
                    text("### :tada: Jubläumszitat - Heute vor $anniversary Jahr${if (anniversary > 1) "en" else ""}")

                    mediaGallery {
                        item("attachment://image.jpg")
                    }
                }
                files += fileUpload
            }.await()
        }

        suspendTransaction {
            ChosenDailyQuotes.insert {
                it[ChosenDailyQuotes.guildIdColumn] = guildId
                it[ChosenDailyQuotes.messageIdColumn] = message.idLong
                it[ChosenDailyQuotes.timestampColumn] = message.timeCreated.toEpochSecond()
            }
        }
    }
}