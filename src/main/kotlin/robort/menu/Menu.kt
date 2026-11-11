package robort.menu

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.SelectOption
import dev.minn.jda.ktx.messages.edit
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import java.awt.Color

suspend fun setMenuCommand(interaction: SlashCommandInteractionEvent) {
    if (interaction.guild == null) {
        interaction.reply(":no_entry: Das Menü kann nur auf einem Server eingerichtet werden").setEphemeral(true).await()
        return
    }

    interaction.reply_ {
        useComponentsV2 = true
        components += mainMenuContainer(interaction.guild!!.idLong)
    }.await()
}

suspend fun mainMenuContainer(guildId: Long) = Container {
    accentColor = Color(0xe4535d)

    suspend fun channelString(channelType: ChannelType) = getGuildSetChannel(guildId, channelType)?.asMention?: "*Nicht gesetzt*"

    text (
        """
            # 📜 Hauptmenü 📜
            *Was empfehlen Sie, Herr rOBERt?*
            
            ## :video_game: Spiele
            
        """.trimIndent()
    )

    actionRow {
        button(ButtonStyle.SECONDARY, "ttt", "TicTacToe", Emoji.fromUnicode("\uD83D\uDD36"))
        button(ButtonStyle.SECONDARY, "ssp", "SchereSteinPapier", Emoji.fromUnicode("✂\uFE0F"))
        button(ButtonStyle.SECONDARY, "stadbahn-guessr", "BahnGuessr", Emoji.fromUnicode("\uD83D\uDE88"))
        button(ButtonStyle.SECONDARY, "meta-gambling", "Meta-Gambling", Emoji.fromUnicode("\uD83C\uDFB0"))
    }

    separator(spacing = Separator.Spacing.LARGE, isDivider = false)
    text ("## :question: Quizze\n")

    actionRow {
        button(ButtonStyle.SECONDARY, "compass", "Kompass", Emoji.fromUnicode("\uD83E\uDDED"))
        button(ButtonStyle.SECONDARY, "informatik-o-mat", "Informatik-o-mat", Emoji.fromUnicode("\uD83D\uDDA5\uFE0F"))
        button(ButtonStyle.SECONDARY, "isitart", "Ist das Kunst?", Emoji.fromUnicode("\uD83C\uDFA8"))
        button(ButtonStyle.SECONDARY, "bigquestions", "Große Fragen", Emoji.fromUnicode("⁉\uFE0F"))
    }

    separator(spacing = Separator.Spacing.LARGE, isDivider = false)
    text ("## :performing_arts: Sonstiges\n")

    actionRow {
        button(ButtonStyle.SECONDARY, "nd", "ND", Emoji.fromUnicode("\uD83E\uDD16"))
        button(ButtonStyle.SECONDARY, "orakel", "Orakel", Emoji.fromUnicode("\uD83D\uDCAB"))
        button(ButtonStyle.SECONDARY, "aura", "Aura-Zähler", Emoji.fromUnicode("\uD83E\uDE99"))
        button(ButtonStyle.SECONDARY, "poll", "Umfrage", Emoji.fromUnicode("\uD83D\uDDF3\uFE0F"))
    }

    separator(spacing = Separator.Spacing.LARGE, isDivider = false)
    text ("## :page_with_curl: Zitate\n")

    text("Zitate Sammlung: ${channelString(ChannelType.QUOTES)}")
    actionRow {
        entitySelectMenu("quote_channel", listOf(EntitySelectMenu.SelectTarget.CHANNEL), valueRange = 0..1, placeholder = "Zitate Sammlung")
    }
    text("Tägliche Zitate: ${channelString(ChannelType.DAILY_QUOTES)}")
    actionRow {
        entitySelectMenu("quote_images_channel", listOf(EntitySelectMenu.SelectTarget.CHANNEL), valueRange = 0..1, placeholder = "Tägliche Zitate")
    }

    separator(spacing = Separator.Spacing.LARGE, isDivider = false)
    text ("## :gear: Einstellungen\n")

    text("Einladungen: ${channelString(ChannelType.INVITES)}")
    actionRow {
        entitySelectMenu("invite_channel", listOf(EntitySelectMenu.SelectTarget.CHANNEL), valueRange = 0..1, placeholder = "Einladungen")
    }

    text("Robort's default Kanal: ${channelString(ChannelType.DEFAULT)}")
    actionRow {
        entitySelectMenu("bot_channel", listOf(EntitySelectMenu.SelectTarget.CHANNEL), valueRange = 0..1, placeholder = "Robort's default Kanal")
    }
}

private val selectIdMapping = mapOf(
    "quote_channel" to ChannelType.QUOTES,
    "quote_images_channel" to ChannelType.DAILY_QUOTES,
    "invite_channel" to ChannelType.INVITES,
    "bot_channel" to ChannelType.DEFAULT,
)

suspend fun processMainMenuSelect(event: EntitySelectInteractionEvent) {
    if (event.customId in selectIdMapping.keys) {
        val channelId = event.interaction.values.firstOrNull()?.idLong

        saveGuildSetChannel(event.guild!!.idLong, selectIdMapping[event.customId]!!, channelId)

        event.message.edit {
            useComponentsV2 = true
            components += mainMenuContainer(event.guild!!.idLong)
        }.await()
    }
}