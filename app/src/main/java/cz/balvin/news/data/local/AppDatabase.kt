package cz.balvin.news.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Feed::class, Article::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {
        /** Ranking articles within a source needs the composite index. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_articles_feedId_publishedAt " +
                        "ON articles (feedId, publishedAt)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "news.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
