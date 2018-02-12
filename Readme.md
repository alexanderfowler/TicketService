How to run TicketService?
To run unit / integration test cases, look into the tst/* folder.
To run the entire program with some sample actions taken by the user, look into the src/main/Launcher.java class.


SeatingProblem solves the issue of reserving seats for groups in a highly concurrent environment. Under the assumption that there is an M by N area and that groups reserving would like to reserve chairs only if they are in a row, the VenueSeating class can be exposed to to customers over a Rest Service to allow them to reserve Seats.

VenueSeating avoids using a global lock for seat reservation so that more customers can reserve seats concurrently. This is accomplished by having row level locks, it would be possible to implement using seat level locks as well.

Seats can be held before being reserved for a limited amount of time. This was accomplished by assigning a timestamp to each seat which marks the time that the hold expires. Whenever another hold request is made or available seats are being counted, the timestamp is checked to see if it has expired. If the timestamp for a seat has expired then the seat is considered to be available.

To verify the functionality of TicketService, I added several unit and integration tests using Junit.


-- Retrospective --
This problem took me much longer than I was expecting because I have not had to write concurrent code involving locks fpr a long time. I am proud that I was able to come up with a solution not involving a global lock, but it would have been much easier and possibly more performant to come up with a simple solution involving a single global lock. If I had more time, I would like to come up with a strategy for load testing the ticket service to see how quickly many customers are able to reserve tickets as well as determine what scaling botttle necks my solution runs into. I would also provide an option for allowing the reservation of seats that span accross multiple rows for a single group.
