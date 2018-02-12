package ticket.service.data;

import java.time.ZonedDateTime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Seat is a POJO used to represent a seat at a venue that can be available,
 * held, or reserved.
 */
@Builder
@Getter
@ToString
public class Seat {
    // specifies a human readable seat id
    private final SeatId seatId;

    // true is the seat is reserved and false otherwise
    @Setter
    private boolean isReserved;

    // Avoid having a global lock every time a seat is held
    // Whenever a seat is held, mark when the seat becomes available.
    @Setter
    private ZonedDateTime heldUntil;

    // Email of the person holding the seat
    // If the seat is sold then this is the email of the person who purchased
    // the seat
    @Setter
    private String lastHeldBy;
}
