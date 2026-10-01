package com.watchparty.service;

import com.watchparty.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final RoomService rooms;

    public ScheduledTasks(RoomService rooms) {
        this.rooms = rooms;
    }

    /** Every 10 s: re-broadcast state of playing rooms so viewers correct small drifts. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void heartbeat() {
        try {
            for (Room room : rooms.activeRooms()) {
                room.broadcastHeartbeat();
            }
            rooms.evictEmptyRooms();
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.toString());
        }
    }

    /** Daily: delete rooms nobody used for a while. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        try {
            rooms.purgeOldRooms();
        } catch (Exception e) {
            log.warn("Purge failed: {}", e.toString());
        }
    }
}
