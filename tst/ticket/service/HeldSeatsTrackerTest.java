package ticket.service;

import static org.junit.Assert.assertEquals;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.junit.Before;
import org.junit.Test;

import ticket.service.data.SeatHold;

public class HeldSeatsTrackerTest {
    private static final int MAX_SEATS_TO_TRACK = 5;

    private HeldSeatsTracker target;

    @Before
    public void setup() {
        ConcurrentHashMap<Integer, SeatHold> heldSeats = new ConcurrentHashMap<>();
        target = new HeldSeatsTracker(heldSeats, MAX_SEATS_TO_TRACK, new Semaphore(1));
    }

    @Test
    public void addAndRetrieveSeatHold() {
        SeatHold seatHold = SeatHold.builder()
                .heldUntil(ZonedDateTime.now().plusSeconds(100))
                .id(1)
                .seatIds(new ArrayList<>())
                .build();
        target.addSeatHold(seatHold, ZonedDateTime.now());
        SeatHold retrievedSeatHold = target.getSeatHold(1, ZonedDateTime.now());
        assertEquals(1, retrievedSeatHold.getId());
    }

    @Test
    public void doNotRetrieveExpiredSeatHold() {
        SeatHold seatHold = SeatHold.builder()
                .heldUntil(ZonedDateTime.now())
                .id(1)
                .seatIds(new ArrayList<>())
                .build();
        target.addSeatHold(seatHold, ZonedDateTime.now());
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // do nothing
        }
        SeatHold retrievedSeatHold = target.getSeatHold(1, ZonedDateTime.now());
        assertEquals(null, retrievedSeatHold);
    }

    @Test
    public void cleanupReducesMapSize() {
        for(int seatId = 0; seatId < MAX_SEATS_TO_TRACK; seatId++) {
            SeatHold seatHold = SeatHold.builder()
                    .heldUntil(ZonedDateTime.now().plusSeconds(2))
                    .id(seatId)
                    .seatIds(new ArrayList<>())
                    .build();
            target.addSeatHold(seatHold, ZonedDateTime.now());
        }
        assertEquals(MAX_SEATS_TO_TRACK, target.heldSeats.size());
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            // do nothing
        }
        for(int seatId = 0; seatId < MAX_SEATS_TO_TRACK; seatId++) {
            SeatHold seatHold = SeatHold.builder()
                    .heldUntil(ZonedDateTime.now().plusSeconds(2))
                    .id(seatId + MAX_SEATS_TO_TRACK)
                    .seatIds(new ArrayList<>())
                    .build();
            target.addSeatHold(seatHold, ZonedDateTime.now());
        }
        // Should have called cleanup in second loop at least once and removed seats from first loop out of the map
        assertEquals(MAX_SEATS_TO_TRACK, target.heldSeats.size());

    }
}
