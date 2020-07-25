package it.mircosoderi.crushare;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface ArchivedMessageDAO {

    @Insert
    void insert(ArchivedMessage archivedMessage);

    @Query("SELECT max(session_id) FROM archived_message")
    long maxSessionId();

    @Query("SELECT max(session_id) FROM archived_message where session_id < :sessionId")
    long prevSessionId(long sessionId);

    @Query("SELECT * FROM archived_message WHERE session_id = :minSessionId and not message_content like 'DESTROY %' order by messageId desc")
    List<ArchivedMessage> getArchivedMessagesDesc(long minSessionId);

    @Query("DELETE FROM archived_message WHERE 1=1")
    void clear();

    @Query("DELETE FROM archived_message WHERE mex_hash = :mexHash")
    void delete(int mexHash);

    @Query("DELETE FROM archived_message WHERE mex_hash = :mexHash and (message_content like :author or author like :author)")
    void destroy(int mexHash, String author);

    @Query("SELECT message_content from archived_message where mex_hash = :mexHash")
    byte[] getRawContent(int mexHash);

    @Query("SELECT message_type from archived_message where mex_hash = :mexHash")
    String getMessageType(int mexHash);

    @Query("SELECT author from archived_message where mex_hash = :mexHash")
    String getMediaAuthor(int mexHash);

    @Query("SELECT signature from archived_message where mex_hash = :mexHash")
    String getMediaSignature(int mexHash);

    @Query("SELECT count(1) from archived_message")
    int getArchiveSize();

    @Query("SELECT * from archived_message where mex_hash = :mexHash")
    List<ArchivedMessage> getMessageByHash(int mexHash);

    @Query("SELECT * from archived_message order by messageId desc limit 1")
    List<ArchivedMessage> getLastMessage();
/*
    @Query("select * from archived_message where messageId = :id")
    List<ArchivedMessage> getCurrMexById(long id);
*/
    @Query("select * from archived_message where messageId = -1+:id")
    List<ArchivedMessage> getPrevMexById(long id);

    @Query("select * from archived_message where messageId = +1+:id")
    List<ArchivedMessage> getNextMexById(long id);

}
