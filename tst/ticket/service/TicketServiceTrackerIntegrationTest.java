package ticket.service;

import static org.junit.Assert.assertEquals;



import java.time.ZonedDateTime;

import main.ServiceModule;

import org.junit.Before;
import org.junit.Test;

import ticket.service.data.SeatHold;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class TicketServiceTrackerIntegrationTest {
    private static final String EMAIL = "example@example.com";
    private VenueSeating target;

    @Before
    public void Setup() {
        Injector injector = Guice.createInjector(new ServiceModule());
        target = injector.getInstance(VenueSeating.class);
    }

    // Ticket service should get initialized, it should have N available seats.
    // N * 1.5 people try to hold tickets.
    // N of the holds succeed, .75 of the holders reserve the tickets.
    // All of the reservations should suceed.
    // Check that there are no tickets immediately available.
    // Check that after enough time has passed, there are .25 * N ticket available.
    @Test
    public void ticketRush() {
        int N = target.numSeatsAvailable();
        for (int count = 0; count < .75 * N; count++) {
            new Thread(() -> {
                SeatHold seatHold = target.findAndHoldSeats(1, EMAIL);
                assertEquals("SUCCESS", target.reserveSeats(seatHold.getId(), EMAIL));
            }).start();
        }

        for (int count = 0; count < .75 * N; count++) {
            new Thread(() -> {
                target.findAndHoldSeats(1, EMAIL);
            }).start();
        }

        assertEquals(0, target.numSeatsAvailable());
        assertEquals((N / 4), target.numSeatsAvailable(ZonedDateTime.now().plusDays(1)));
    }
}
