package main;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import ticket.service.HeldSeatsTracker;
import ticket.service.TicketService;
import ticket.service.VenueSeating;
import ticket.service.data.Seat;
import ticket.service.data.SeatHold;
import ticket.service.data.SeatId;
import ticket.service.data.SeatRow;

import com.google.inject.AbstractModule;

/**
 * ServiceModule is used for dependency injection for the SeatingProblem.
 */
public class ServiceModule extends AbstractModule {

    private static final int NUM_ROWS = 30;
    private static final int NUM_SEATS_PER_ROW = 20;
    private static final int SECONDS_TO_HOLD_SEATS = 60 * 15;

    /*
     * (non-Javadoc)
     * 
     * @see com.google.inject.AbstractModule#configure()
     */
    @Override
    protected void configure() {
        SeatRow[] rowNumsToRows = buildRowNumsToRows();
        int maxSeatsToHold = NUM_ROWS * NUM_SEATS_PER_ROW;
        HeldSeatsTracker heldSeatsTracker = new HeldSeatsTracker(
                new ConcurrentHashMap<Integer, SeatHold>(maxSeatsToHold),
                NUM_ROWS * NUM_SEATS_PER_ROW, new Semaphore(1));
        VenueSeating venueSeating = new VenueSeating(rowNumsToRows,
                SECONDS_TO_HOLD_SEATS, new AtomicInteger(0), heldSeatsTracker);
        bind(TicketService.class).toInstance(venueSeating);
        // This is exposed for integration testing of venue seating
        // ideally the integration test would not rely on the ServiceModule
        // and VenueSeating  could have been constructed independently.
        bind(VenueSeating.class).toInstance(venueSeating);
    }

    /**
     * @return Array mapping seat row number to SeatRow object.
     */
    private SeatRow[] buildRowNumsToRows() {
        SeatRow[] seatRows = new SeatRow[NUM_ROWS];
        for (int rowNum = 0; rowNum < NUM_ROWS; rowNum++) {
            List<Seat> seats = new ArrayList<>();
            for (int seatNum = 0; seatNum < NUM_SEATS_PER_ROW; seatNum++) {
                Seat seat = Seat
                        .builder()
                        .isReserved(false)
                        .seatId(SeatId.builder().rowNum(rowNum)
                                .seatNum(seatNum).build()).build();
                seats.add(seat);
            }
            SeatRow seatRow = new SeatRow(seats, new Semaphore(1));
            seatRows[rowNum] = seatRow;
        }

        return seatRows;
    }
}
