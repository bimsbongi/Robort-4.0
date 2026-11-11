package robort

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import robort.Aura.userId
import robort.menu.ChannelType
import robort.quiz.QuizType
import robort.utils.envOrDefault


private var databaseConnected = false
/**
 * Set up a database connection to the standard postgres DB
 */
suspend fun setupDatabase() {
    if (databaseConnected) return

    val dbHost = envOrDefault("DB_HOST", "localhost")
    val dbPort = envOrDefault("DB_PORT", "5432")
    val dbName = envOrDefault("DB_NAME", "robort")
    val dbUser = envOrDefault("DB_USER", "postgres")
    val dbPassword = envOrDefault("DB_PASSWORD", "postgres")
    R2dbcDatabase.connect(url = "r2dbc:postgresql://$dbHost:$dbPort/$dbName", user = dbUser, password = dbPassword)
    databaseConnected = true

    suspendTransaction {
        // Initialize all tables
        SchemaUtils.create(
            QuizAnswers,
            Money,
            Aura,
            AuraTransactions,
            GuildChannelSettings
        )
    }
}

object QuizAnswers : Table("quiz_answers") {
    val userId = long("user_id")
    val questionId = integer("question_id")
    val answerId = integer("answer_id")
    val type = enumerationByName<QuizType>("quiz_type", 64)

    override val primaryKey = PrimaryKey(userId, questionId, type)
}

object Money : Table("money") {
    val userId = long("user_id")
    val money = long("money")

    override val primaryKey = PrimaryKey(userId)
}

object Aura : Table("aura") {
    val userId = long("user_id")
    val aura = long("aura")

    override val primaryKey = PrimaryKey(userId)
}

object AuraTransactions : Table("aura_transactions") {
    val timestamp = long("timestamp")
    val userId = long("user_id")
    val bookerId = long("booker")
    val change = long("change")
    val auraAfter = long("aura_after")
    val reason = text("reason")
}


object GuildChannelSettings: Table("channel_settings") {
    val guildId = long("guild_id")
    val channelId = long("channel_id").nullable()
    val channelType = enumerationByName<ChannelType>("channel_type", 64)

    override val primaryKey = PrimaryKey(guildId, channelType)
}
