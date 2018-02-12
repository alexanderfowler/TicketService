package ticket.service.data;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * SeatRow tracks free and reserved seats in a concurrent manner for a single
 * row.
 */
public class SeatRow {
    // Seats in the row. They must be kept in order
    // seats.get(0) should returns seat 1 and seats.get(1) should return seat 2
    // which is next to seat one
    private final List<Seat> seats;
    // Lookup on seats by id
    private final Map<SeatId, Seat> seatIdsToSeats;
    private final Semaphore semaphore;

    /**
     * @param seats
     *            Seats in the row.
     * @param semaphore
     *            Lock for the seatRow.
     */
    public SeatRow(final List<Seat> seats, final Semaphore semaphore) {
        this.seats = seats;
        this.seatIdsToSeats = new HashMap<>(seats.size());
        for (Seat seat : seats) {
            this.seatIdsToSeats.put(seat.getSeatId(), seat);
        }
        this.semaphore = semaphore;
    }

    /**
     * @param time
     *            Time that seat availability is being checked.
     * @return Approximate number of seats that are available. Due to
     *         concurrency issues where seats can be reserved while seat
     *         availability is being counted, this method may under or
     *         overestimate the exact number of seats that are available at any
     *         given time.
     */
    public int numSeatsAvailable(final ZonedDateTime time) {
        int numAvailableSeats = 0;
        for (Seat seat : seats) {
            if (isSeatAvailable(seat, time)) {
                numAvailableSeats++;
            }
        }
        return numAvailableSeats;
    }

    /**
     * @param seat
     *            Seat to check availability for.
     * @param time
     *            Time that availability is being checked.
     * @return True if the seat is available and false otherwise.
     */
    private boolean isSeatAvailable(final Seat seat, final ZonedDateTime time) {
        if (!seat.isReserved()) {
            // Seat has never been held
            if (seat.getHeldUntil() == null) {
                return true;
            }
            // Seat has been held but the hold expired
            if (seat.getHeldUntil().isBefore(time)) {
                return true;
            }
        }

        // Either seat was reserved or it is being held
        return false;
    }

    /**
     * Assume that customers want to sit together in the same row. Will not
     * reserve separate seats for customers. Will not lock row in the event of
     * resource contention, so it is possible that there is a seat available in
     * this row which was not returned to a customer since someone else was
     * reserving seats at the same time.
     * 
     * @param numSeats
     *            Number of seats to hold for a customer.
     * @param customerEmail
     *            Email of the customer holding the seats.
     * @param reservationTime
     *            Time that the hold was requested.
     * @param secondsToHold
     *            Number of seconds to hold a seat for.
     * @return List of seat ids being held or null if no seats could be held.
     */
    public List<SeatId> holdSeatsNonBlocking(final int numSeatsToHold,
            final String customerEmail, final ZonedDateTime reservationTime,
            final int secondsToHold) {
        List<SeatId> heldSeats = null;
        boolean wasAcquired = semaphore.tryAcquire();
        if (wasAcquired) {
            try {
                heldSeats = holdSeatsAfterLockAcquired(numSeatsToHold,
                        customerEmail, reservationTime, secondsToHold);
            } finally {
                semaphore.release();
            }
        }
        return heldSeats;
    }

    /**
     * Assume that customers want to sit together in the same row. Will not
     * reserve separate seats for customers. Will lock row in the event of
     * resource contention, so it is not possible that there is a seat available
     * in this row which was not returned to a customer. This method can take a
     * significant amount of time to complete since there could be many threads
     * trying to find an available seat at the same time.
     * 
     * @param numSeats
     *            Number of seats to hold for a customer.
     * @param customerEmail
     *            Email of the customer holding the seats.
     * @param reservationTime
     *            Time that the hold was requested.
     * @param secondsToHold
     *            Number of seconds to hold a seat for.
     * @return List of seat ids being held or null if no seats could be held.
     */
    // If we are ok with occasionally missing seats for customers
    // we could remove this method. The benefit to doing this would be a greatly
    // reduced change of our service going down under significant load.
    public List<SeatId> holdSeatsBlocking(final int numSeatsToHold,
            final String customerEmail, final ZonedDateTime reservationTime,
            final int secondsToHold) {
        List<SeatId> heldSeats = null;
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try {
            heldSeats = holdSeatsAfterLockAcquired(numSeatsToHold,
                    customerEmail, reservationTime, secondsToHold);
        } finally {
            semaphore.release();
        }
        return heldSeats;
    }

    /**
     * After lock has been acquired for this row, perform non-concurrent
     * operations to get seats for a customer request. Has undefined behavior if
     * the lock was not acquired.
     * 
     * @param numSeats
     *            Number of seats to hold for a customer.
     * @param customerEmail
     *            Email of the customer holding the seats.
     * @param reservationTime
     *            Time that the hold was requested.
     * @param secondsToHold
     *            Number of seconds to hold a seat for.
     * @return Seats being held for a customer or null if seats were not
     *         available.
     */
    private List<SeatId> holdSeatsAfterLockAcquired(final int numSeatsToHold,
            final String customerEmail, final ZonedDateTime reservationTime,
            final int secondsToHoldSeat) {
        int numAvailableAdjacentSeats = 0;
        for (int seatNum = 0; seatNum < seats.size(); seatNum++) {
            Seat seat = seats.get(seatNum);
            if (isSeatAvailable(seat, reservationTime)) {
                numAvailableAdjacentSeats++;
            }
            if (numAvailableAdjacentSeats == numSeatsToHold) {
                ZonedDateTime heldUntil = reservationTime
                        .plusSeconds(secondsToHoldSeat);
                List<SeatId> heldSeats = new ArrayList<>(numSeatsToHold);
                for (int numHeldSeats = 0; numHeldSeats < numSeatsToHold; numHeldSeats++) {
                    seat = seats.get(seatNum - numHeldSeats);
                    seat.setHeldUntil(heldUntil);
                    seat.setLastHeldBy(customerEmail);
                    heldSeats.add(seat.getSeatId());
                }
                return heldSeats;
            }
        }
        // Could not find enough seats to hold
        return null;
    }

    /**
     * @param seatIds
     *            Ids of seats being reserved.
     * @param customerEmail
     *            Email of customer reserving the seats.
     * @return true if the seats were reserved successfully and false otherwise.
     */
    public boolean reserveSeatsBlocking(final List<SeatId> seatIds,
            final String customerEmail) {
        boolean saleWasCompleted = false;
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        try {
            boolean saleIsValid = verifySeatsAreReservable(seatIds, customerEmail);
            if (saleIsValid) {
                // sell seats
                for (SeatId seatId : seatIds) {
                    Seat seat = seatIdsToSeats.get(seatId);
                    seat.setReserved(true);
                }
                saleWasCompleted = true;
            }
        } finally {
            semaphore.release();
        }
        return saleWasCompleted;
    }

    /**
     * Check that the seats are in this row and that they are being held
     * by the customer trying to reserve them.
     * @param seatIds Ids of seats to check.
     * @param customerEmail Email of customer trying to reserve the seats.
     * @return True is the seats are reservable by the customer and false otherwise.
     */
    private boolean verifySeatsAreReservable(final List<SeatId> seatIds,
            final String customerEmail) {
        for (SeatId seatId : seatIds) {
            Seat seat = seatIdsToSeats.get(seatId);
            // Wrong row
            if (seat == null) {
                return false;
            } else {
                // Seat was already sold or it has been held by a different
                // customer
                if (seat.isReserved() == true
                        || !Objects.equals(seat.getLastHeldBy(), customerEmail)) {
                    return false;
                }
            }
        }
        return true;
    }
}
