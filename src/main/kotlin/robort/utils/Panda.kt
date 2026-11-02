package robort.utils

import dev.minn.jda.ktx.coroutines.await
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private fun generatePandaWorld(bamboo:Int, panda:Int) = buildString {
    repeat(10) {
        when (it) {
            panda -> append(":panda_face:")
            bamboo -> append(":bamboo:")

            else -> append("⠀⠀")
        }
    }
}

suspend fun animatePanda(interaction: SlashCommandInteractionEvent) {

    var bamboo = Random.nextInt(1,10)
    var panda = 0

    interaction.reply(generatePandaWorld(bamboo, panda)).await()

    repeat(100) {
        delay(1.seconds)

        if (bamboo == panda) {
            val rand = Random.nextInt(0,9)
            bamboo = rand + if (rand >= panda) 1 else 0
        } else if (bamboo < panda) {
            panda -= 1
        } else {
            panda += 1
        }
        interaction.hook.editOriginal(generatePandaWorld(bamboo, panda)).await()
    }
    interaction.hook.editOriginal(generatePandaWorld(bamboo, panda) + " Panda satt!").await()
}