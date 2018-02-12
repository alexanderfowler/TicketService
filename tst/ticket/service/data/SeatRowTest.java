package ticket.service.data;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class SeatRowTest {
    private static final int RESERVATION_SECONDS = 999;
    private static final String EMAIL = "example@example.com";
    private static final int NUM_AVAILABLE_SEATS = 100;
    private SeatRow target;

    @Before
    public void setup() {
        List<Seat> seats = new ArrayList<>();
        for (int seatNum = 0; seatNum < NUM_AVAILABLE_SEATS; seatNum++) {
            Seat seat = Seat
                    .builder()
                    .isReserved(false)
                    .seatId(SeatId.builder().rowNum(0).seatNum(seatNum).build())
                    .build();
            seats.add(seat);
        }
        target = new SeatRow(seats, new Semaphore(1));
    }

    // Checks seats available is equals to the total number of available seats.
    // Try to hold 100 seats without blocking and expect some threads to fail.
    // Check seats available is some number larger than 0 but smaller than the
    // total number of potential available seats.
    @Test
    public void onlySomeNonBlockingHoldsSucceed() {
        assertEquals(NUM_AVAILABLE_SEATS,
                target.numSeatsAvailable(ZonedDateTime.now()));
        int numSeatsPerGroup = 5;
        for (int count = 0; count < NUM_AVAILABLE_SEATS / numSeatsPerGroup; count++) {
            new Thread(() -> target.holdSeatsNonBlocking(numSeatsPerGroup,
                    "example@example.com", ZonedDateTime.now(), RESERVATION_SECONDS)).start();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
        assertTrue(target.numSeatsAvailable(ZonedDateTime.now()) < NUM_AVAILABLE_SEATS);
        assertTrue(target.numSeatsAvailable(ZonedDateTime.now()) > 0);
    }

    // Checks seats available is equals to the total number of available seats.
    // Try to hold 100 seats with blocking and expect all threads to succeed.
    // Check seats available is exactly 0.
    @Test
    public void allBlockingHoldsSucceed() {
        assertEquals(NUM_AVAILABLE_SEATS,
                target.numSeatsAvailable(ZonedDateTime.now()));
        for (int count = 0; count < NUM_AVAILABLE_SEATS / 5; count++) {
            new Thread(() -> target.holdSeatsBlocking(5, EMAIL,
                    ZonedDateTime.now(), RESERVATION_SECONDS)).start();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
        assertEquals(0, target.numSeatsAvailable(ZonedDateTime.now()));
    }

    // Hold all seats (blocking) but reseve half of the seats
    // the held seats should become available but the reserved seats should
    // not become available
    @Test
    public void reservedSeatsDoNotBecomeAvailableAvailable() {
        assertEquals(NUM_AVAILABLE_SEATS,
                target.numSeatsAvailable(ZonedDateTime.now()));
        for (int count = 0; count < NUM_AVAILABLE_SEATS / 5; count++) {
            final int immutableCountCopy = count;
            new Thread(() -> {
                List<SeatId> seatIds = target.holdSeatsBlocking(5, EMAIL,ZonedDateTime.now(), RESERVATION_SECONDS);
                if (immutableCountCopy % 2 == 0) {
                    target.reserveSeatsBlocking(seatIds, EMAIL);
                }
            }).start();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
        assertEquals(0, target.numSeatsAvailable(ZonedDateTime.now()));
        // Lookup the number of available seats in the future
        assertEquals(50, target.numSeatsAvailable(ZonedDateTime.now().plusSeconds(RESERVATION_SECONDS + 5)));
    }

    // claiming a seat reservation from an incorrect row should fail
    @Test
    public void cannotReserveSeatFromWrongRow() {
        List<SeatId> seatIds = target.holdSeatsBlocking(5, EMAIL,ZonedDateTime.now(), RESERVATION_SECONDS);
        List<SeatId> badClones = new ArrayList<>();
        for (SeatId seatId : seatIds) {
            SeatId badClone = SeatId.builder()
                    .rowNum(seatId.getRowNum() + 1)
                    .seatNum(seatId.getSeatNum())
                    .build();
            badClones.add(badClone);
        }
        boolean wasReserved = target.reserveSeatsBlocking(badClones, EMAIL);
        assertFalse(wasReserved);
    }

    // claiming a seat reservation from an incorrect email should fail
    @Test
    public void cannotReserveSeatFromWrongEmail() {
        List<SeatId> seatIds = target.holdSeatsBlocking(5, EMAIL,ZonedDateTime.now(), RESERVATION_SECONDS);
        boolean wasReserved = target.reserveSeatsBlocking(seatIds, "BAD EMAIL");
        assertFalse(wasReserved);
    }

    // claiming a seat reservation when the seat has never been held should fail
    @Test
    public void cannotReserveUnheldSeat() {
        List<SeatId> seatIds = new ArrayList<>();
        for (int seatNum = 0; seatNum < 5; seatNum++) {
            SeatId seatId = SeatId.builder()
                    .rowNum(0)
                    .seatNum(seatNum)
                    .build();
            seatIds.add(seatId);
        }
        boolean wasReserved = target.reserveSeatsBlocking(seatIds, EMAIL);
        assertFalse(wasReserved);
    }
}
