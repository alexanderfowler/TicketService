package ticket.service;

import java.time.ZonedDateTime;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import com.google.common.annotations.VisibleForTesting;

import ticket.service.data.SeatHold;

/**
 * HeldSeatsTracker keeps track of seats that are being held for customers by
 * the ticket service.
 */
public class HeldSeatsTracker {

    @VisibleForTesting
    protected final ConcurrentHashMap<Integer, SeatHold> heldSeats;
    private final int maxSeatsToHold;
    private final Semaphore cleanupSemaphore;

    /**
     * @param heldSeats
     *            Map to track seats that are being held.
     * @param maxSeatsToHold
     *            Max number of seats to hold at once.
     * @param cleanupSemaphore
     *            Lock which keeps multiple threads from holding the same seats.
     */
    public HeldSeatsTracker(
            final ConcurrentHashMap<Integer, SeatHold> heldSeats,
            final int maxSeatsToHold, final Semaphore cleanupSemaphore) {
        this.heldSeats = heldSeats;
        this.maxSeatsToHold = maxSeatsToHold;
        this.cleanupSemaphore = cleanupSemaphore;
    }

    public void addSeatHold(final SeatHold seatHold,
            final ZonedDateTime holdTime) {
        // cleanup map if number of seats in map is approaching the maximum
        // only one thread will be devoted to cleaning at once since cleanupExpiredSeatHolds
        // tries to acquire a lock internally
        if (heldSeats.size() >= maxSeatsToHold * .8) {
            cleanupExpiredSeatHolds(holdTime);
        }
        heldSeats.put(seatHold.getId(), seatHold);
    }

    /**
     * @param id
     *            id of a seat hold
     * @param time
     *            that hold is being requested for.
     * @return SeatHold object contained details of the hold request.
     */
    public SeatHold getSeatHold(final int id, final ZonedDateTime time) {
        SeatHold seatHold = heldSeats.get(id);
        // seatHold may have been removed from the map during cleanup
        if (seatHold == null) {
            return null;
        } else {
            // hold has expired so do not return it
            if (seatHold.getHeldUntil().isBefore(time)) {
                return null;
            } else {
                return seatHold;
            }
        }
    }

    /**
     * @param heldTime
     *            time the cleanup is being performed.
     */
    private void cleanupExpiredSeatHolds(final ZonedDateTime heldTime) {
        boolean acquired = cleanupSemaphore.tryAcquire();
        // only have a single thread cleaning up the map at any given time
        // allow more elements to be added to the map than maxSeatsToHold
        // temporarily while the map is being cleaned
        if (acquired) {
            // perform this cleanup asnchronously so that
            // no customer has their request held up for the cleanup process
            new Thread(() -> cleanup(heldTime)).start();
        }
    }

    /**
     * @param heldTime
     *            time cleanup is being performed.
     * @return null.
     */
    private Void cleanup(final ZonedDateTime heldTime) {
        try {
            for (Entry<Integer, SeatHold> idToSeatHold : heldSeats.entrySet()) {
                if (idToSeatHold.getValue().getHeldUntil().isBefore(heldTime)) {
                    heldSeats.remove(idToSeatHold.getKey());
                }
            }
        } finally {
            cleanupSemaphore.release();
        }
        return null;
    }

}
