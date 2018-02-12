package ticket.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.annotations.VisibleForTesting;

import ticket.service.data.SeatHold;
import ticket.service.data.SeatId;
import ticket.service.data.SeatRow;

/**
 * VenueSeating defines all seats that are available at a venue. It is used to
 * track the status of a seat (available, temporarilyReserved, sold). Currently
 * it assumes that seats are defined in a square grid because this is the
 * example that was given in the practice problem definition. VenueSeating could
 * be modified if the venue had a different seating arrangement (e.g. a sporting
 * event with many sections)
 */
public class VenueSeating implements TicketService {

    private static final String FAILED_RESERVATION_CODE = "FAIL";
    private static final String SUCCESSFUL_RESERVATION_CODE = "SUCCESS";

    private final SeatRow[] rowNumsToRows;
    private final int secondsToHoldSeats;
    private final AtomicInteger seatIdGenerator;
    private HeldSeatsTracker heldSeatsTracker;

    /**
     * @param rowNumsToRows
     *            Map row number to row.
     * @param secondsToHoldSeats
     *            number of seconds to hold each seat after a seatHoldRequest.
     * @param seatIdGenerator
     *            Id of a seat hold request. Ids can be reused after they have
     *            expired.
     * @param heldSeatsTracker
     *            Tracks seats that are being held.
     */
    public VenueSeating(final SeatRow[] rowNumsToRows,
            final int secondsToHoldSeats, final AtomicInteger seatIdGenerator,
            final HeldSeatsTracker heldSeatsTracker) {
        this.rowNumsToRows = rowNumsToRows;
        this.secondsToHoldSeats = secondsToHoldSeats;
        this.seatIdGenerator = seatIdGenerator;
        this.heldSeatsTracker = heldSeatsTracker;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ticket.service.TicketService#numSeatsAvailable()
     */
    @Override
    public int numSeatsAvailable() {
        ZonedDateTime time = ZonedDateTime.now();
        return numSeatsAvailable(time);
    }

    /**
     * Check the number of seats available at a specified time. This function
     * is protected so that tests can check the number of seats available in the future.
     * @param zonedDateTime Check how many seats are available at this time.
     * @return Number of seats available at the time specified by zonedDateTime.
     */
    @VisibleForTesting
    protected int numSeatsAvailable(final ZonedDateTime time) {
        int numSeatsAvailable = 0;
        for (int index = 0; index < rowNumsToRows.length; index++) {
            SeatRow seatRow = rowNumsToRows[index];
            numSeatsAvailable += seatRow.numSeatsAvailable(time);
        }

        return numSeatsAvailable;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ticket.service.TicketService#findAndHoldSeats(int, java.lang.String)
     */
    @Override
    public SeatHold findAndHoldSeats(final int numSeats,
            final String customerEmail) {
        ZonedDateTime holdTime = ZonedDateTime.now();
        ZonedDateTime heldUntil = holdTime.plusSeconds(secondsToHoldSeats);
        // Look in each of the rows starting with the first one to see if
        // it is possible to reserve seats. If another customer has locked
        // a row (happens during reservation) then skip it and look in the next
        // row
        for (int index = 0; index < rowNumsToRows.length; index++) {
            SeatRow seatRow = rowNumsToRows[index];
            List<SeatId> seatIds = seatRow.holdSeatsNonBlocking(numSeats,
                    customerEmail, holdTime, secondsToHoldSeats);
            if (seatIds != null) {
                return buildSeatHold(heldUntil, seatIds, holdTime);
            }
        }
        // After looking in all rows without acquiring locks, look again
        // while acquiring locks. This is significantly slower so we don't
        // default
        // to it.
        for (int index = 0; index < rowNumsToRows.length; index++) {
            SeatRow seatRow = rowNumsToRows[index];
            List<SeatId> seatIds = seatRow.holdSeatsBlocking(numSeats,
                    customerEmail, holdTime, secondsToHoldSeats);
            if (seatIds != null) {
                return buildSeatHold(heldUntil, seatIds, holdTime);
            }
        }

        // We still could not acquire any seats so return null
        // and let the caller decide what to do.
        return null;
    }

    /**
     * @param heldUntil
     *            time that hold expires.
     * @param seatIds
     *            Ids of seats that are being held.
     * @param holdTime
     *            time that hold request is being made.
     * @return
     */
    private SeatHold buildSeatHold(final ZonedDateTime heldUntil,
            final List<SeatId> seatIds, final ZonedDateTime holdTime) {
        SeatHold seatHold = SeatHold.builder()
                .id(seatIdGenerator.getAndIncrement()).seatIds(seatIds)
                .heldUntil(heldUntil).build();
        heldSeatsTracker.addSeatHold(seatHold, holdTime);
        return seatHold;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ticket.service.TicketService#reserveSeats(int, java.lang.String)
     */
    @Override
    public String reserveSeats(int seatHoldId, String customerEmail) {
        SeatHold seatHold = heldSeatsTracker.getSeatHold(seatHoldId,
                ZonedDateTime.now());
        if (seatHold == null) {
            return FAILED_RESERVATION_CODE;
        }
        List<SeatId> seatIds = seatHold.getSeatIds();
        int rowNum = seatIds.get(0).getRowNum();

        boolean saleWasValid = rowNumsToRows[rowNum].reserveSeatsBlocking(
                seatIds, customerEmail);
        // sale could be invalid if the hold expired while the reserve seats
        // process
        // was happening
        if (!saleWasValid) {
            return FAILED_RESERVATION_CODE;
        }

        // There is no way to look up a successful reservation so for now
        // just return a positive code. If this were implemented in production
        // then we would have some sort of db that maps successful codes to
        // reservation details.
        return SUCCESSFUL_RESERVATION_CODE;
    }

}
