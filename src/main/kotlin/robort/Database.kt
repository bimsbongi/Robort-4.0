package robort

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
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
            GuildChannelSettings,
            QuoteMessages,
            ChosenDailyQuotes
        )
    }
}

object QuizAnswers : Table("quiz_answers") {
    val userIdColumn = long("user_id")
    val questionIdColumn = integer("question_id")
    val answerIdColumn = integer("answer_id")
    val typeColumn = enumerationByName<QuizType>("quiz_type", 64)

    override val primaryKey = PrimaryKey(userIdColumn, questionIdColumn, typeColumn)
}

object Money : Table("money") {
    val userIdColumn = long("user_id")
    val moneyColumn = long("money")

    override val primaryKey = PrimaryKey(userIdColumn)
}

object Aura : Table("aura") {
    val userIdColumn = long("user_id")
    val auraColumn = long("aura")

    override val primaryKey = PrimaryKey(userIdColumn)
}

object AuraTransactions : Table("aura_transactions") {
    val timestampColumn = long("timestamp")
    val userIdColumn = long("user_id")
    val bookerIdColumn = long("booker")
    val changeColumn = long("change")
    val auraAfterColumn = long("aura_after")
    val reasonColumn = text("reason")
}


object GuildChannelSettings: Table("channel_settings") {
    val guildIdColumn = long("guild_id")
    val channelIdColumn = long("channel_id").nullable()
    val channelTypeColumn = enumerationByName<ChannelType>("channel_type", 64)

    override val primaryKey = PrimaryKey(guildIdColumn, channelTypeColumn)
}

object QuoteMessages: Table("quote_messages") {
    val messageIdColumn = long("message_id")
    val channelIdColumn = long("channel_id")
    val guildIdColumn = long("guild_id")
    val timestampColumn = long("timestamp")
    val cachedContentColumn = text("cached_content")

    override val primaryKey = PrimaryKey(messageIdColumn)
}

object ChosenDailyQuotes: Table("chosen_daily_quotes") {
    val messageIdColumn = long("message_id")
    val guildIdColumn = long("guild_id")
    val timestampColumn = long("timestamp")
}
