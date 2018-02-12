package ticket.service.data;

import java.time.ZonedDateTime;
import java.util.List;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * SeatHold represents a request from customers to hold a group of seats. It
 * contains a reference to the seats which are being held as well as the time
 * that the request expires.
 */
@Getter
@Builder
@EqualsAndHashCode
@ToString
public class SeatHold {
    private final int id;
    @NonNull
    private final List<SeatId> seatIds;
    // Time that the hold expires.
    @NonNull
    private final ZonedDateTime heldUntil;
}
