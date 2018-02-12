package main;

import ticket.service.TicketService;
import ticket.service.data.SeatHold;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * Command line entry class for the ticket service. In practice this would likely
 * by a rest service with APIs exposed around urls. For this practice problem,
 * the command line entry point is used to give an example of how the ticket service
 * could be used by rest apis.
 */
public class Launcher {
    /**
     * Command line entry point.
     * @param args Command line arguments.
     */
    public static void main(final String [] args) {
        // Use Guice for dependency injection.
        Injector injector = Guice.createInjector(new ServiceModule());
        TicketService ticketService = injector.getInstance(TicketService.class);

        // Show available seats
        System.out.println(ticketService.numSeatsAvailable());
        // Find and hold some of them
        String email = "Serhat.Aydemir@walmart.com";
        SeatHold seatHold = ticketService.findAndHoldSeats(5, "Serhat.Aydemir@walmart.com");
        String reservationCode = ticketService.reserveSeats(seatHold.getId(), email);
        // Show that reservation was accepted
        System.out.println(reservationCode);
        // Show availabe seats has been reduced
        System.out.println(ticketService.numSeatsAvailable());
    }
}
