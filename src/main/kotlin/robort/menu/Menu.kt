package robort.menu

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.interactions.components.SelectOption
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.components.buttons.ButtonStyle
import net.dv8tion.jda.api.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import java.awt.Color

suspend fun setMenuCommand(interaction: SlashCommandInteractionEvent) {
    interaction.reply_ {
        useComponentsV2 = true
        components += mainMenuContainer()
    }.await()
}

fun mainMenuContainer() = Container {
    accentColor = Color(0xe4535d)
    text (
        """
            # 📜 Hauptemenü 📜
            *Was empfehlen sie, Herr rOBERt?*
            
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
    text ("## :question: Quizze")

    actionRow {
        button(ButtonStyle.SECONDARY, "compass", "Kompass", Emoji.fromUnicode("\uD83E\uDDED"))
        button(ButtonStyle.SECONDARY, "informatik-o-mat", "Informatik-o-mat", Emoji.fromUnicode("\uD83D\uDDA5\uFE0F"))
        button(ButtonStyle.SECONDARY, "isitart", "Ist das Kunst?", Emoji.fromUnicode("\uD83C\uDFA8"))
        button(ButtonStyle.SECONDARY, "bigquestions", "Große Fragen", Emoji.fromUnicode("⁉\uFE0F"))
    }

    separator(spacing = Separator.Spacing.LARGE, isDivider = false)
    text ("## :performing_arts: Sonstiges")

    actionRow {
        button(ButtonStyle.SECONDARY, "nd", "ND", Emoji.fromUnicode("\uD83E\uDD16"))
        button(ButtonStyle.SECONDARY, "orakel", "Orakel", Emoji.fromUnicode("\uD83D\uDCAB"))
        button(ButtonStyle.SECONDARY, "aura", "Aura-Zähler", Emoji.fromUnicode("\uD83E\uDE99"))
        button(ButtonStyle.SECONDARY, "poll", "Umfrage", Emoji.fromUnicode("\uD83D\uDDF3\uFE0F"))
    }

    separator(spacing = Separator.Spacing.LARGE, isDivider = false)
    text ("## :page_with_curl: Zitate")

    text("Zitate Sammlung: PLACEHOLDER")
    actionRow {
        entitySelectMenu("quote_channel", listOf(EntitySelectMenu.SelectTarget.USER), placeholder = "Zitate Sammlung")
    }
    text("Tägliche Zitate: PLACEHOLDER")
    actionRow {
        entitySelectMenu("quote_images_channel", listOf(EntitySelectMenu.SelectTarget.USER), placeholder = "Tägliche Zitate")
    }

    separator(spacing = Separator.Spacing.LARGE, isDivider = false)
    text ("## :gear: Einstellungen")

    text("Einladungen: PLACEHOLDER")
    actionRow {
        entitySelectMenu("invite_channel", listOf(EntitySelectMenu.SelectTarget.CHANNEL), placeholder = "Einladungen")
    }

    text("Robort's default Kanal: PLACEHOLDER")
    actionRow {
        entitySelectMenu("bot_channel", listOf(EntitySelectMenu.SelectTarget.CHANNEL), placeholder = "Robort's default Kanal")
    }
}