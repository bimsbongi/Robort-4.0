package robort.utils

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.Container
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.upsertReturning
import robort.Aura
import robort.AuraTransactions
import java.awt.Color

private suspend fun bookAura(booker: User, user: User, amount:Long, reason: String): Container {
    if (user == booker && amount > 0) {
        return Container {
            accentColor = Color(0xffb800)
            text("## :unamused: Cringe, ${user.asMention} versucht die eigene Aura zu erhöhen\n*tztztz*")
        }
    }

    val userAura = suspendTransaction {
        Aura.upsertReturning(
            onUpdate = { it[Aura.aura] = Aura.aura + amount },
            body = {
                it[Aura.userId] = user.idLong
                it[Aura.aura] = amount
            }

        ).first()[Aura.aura]
    }

    if (amount != 0L) {
        suspendTransaction {
            AuraTransactions.insert {
                it[AuraTransactions.timestamp] = System.currentTimeMillis()
                it[AuraTransactions.userId] = user.idLong
                it[AuraTransactions.auraAfter] = userAura
                it[AuraTransactions.change] = amount
                it[AuraTransactions.reason] = reason
                it[AuraTransactions.bookerId] = booker.idLong
            }
        }
    }

    val auraInfo = if (amount == 0L) {
        buildString {
            append("### Aura von ${user.asMention}:\n# _${abbreviateNumber(userAura)}_\n")

            val transactions = suspendTransaction {
                AuraTransactions.selectAll().where { AuraTransactions.userId eq user.idLong }.map { it[AuraTransactions.reason] to it[AuraTransactions.change] }.toList()
            }

            transactions.forEach {
                append("**${abbreviateNumber(it.second)}** \t${it.first}\n")
            }
        }
    } else {
        val emoji = if (amount > 0) ":up_arrow:" else ":down_arrow:"

        "### Aura von ${user.asMention}:\n# _${abbreviateNumber(userAura - amount)}_ -> _${abbreviateNumber(userAura)}_ $emoji"
    }

    return Container {
        accentColor = Color(0xffb800)
        text("## :coin: Aurazähler\n" + auraInfo + "\n$reason")
    }
}

suspend fun bookAuraCommand(interaction: SlashCommandInteractionEvent) {
    val user = interaction.getOption("user")?.asUser?: error("No aura user")
    val amount = interaction.getOption("amount")?.asDouble ?: 0
    val reason = interaction.getOption("reason")?.asString ?: ""

    interaction.reply_ {
        useComponentsV2 = true
        components += bookAura(interaction.user, user, amount.toLong(), reason)
    }.await()
}