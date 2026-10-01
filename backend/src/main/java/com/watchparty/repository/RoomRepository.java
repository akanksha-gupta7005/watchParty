package com.watchparty.repository;

import com.watchparty.entity.RoomEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<RoomEntity, String> {

    List<RoomEntity> findByUpdatedAtBefore(Instant cutoff);
}
