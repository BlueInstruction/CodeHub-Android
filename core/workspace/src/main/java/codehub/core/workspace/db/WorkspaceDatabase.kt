package codehub.core.workspace.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WorkspaceDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
