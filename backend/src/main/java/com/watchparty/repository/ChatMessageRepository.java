package com.watchparty.repository;

import com.watchparty.entity.ChatMessageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findTop50ByRoomCodeOrderByIdDesc(String roomCode);

    void deleteByRoomCode(String roomCode);
}
