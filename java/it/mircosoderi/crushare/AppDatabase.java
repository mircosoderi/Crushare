package it.mircosoderi.crushare;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {ArchivedMessage.class}, version = 8)
public abstract class AppDatabase extends RoomDatabase{
    public abstract ArchivedMessageDAO archivedMessageDao();
}
