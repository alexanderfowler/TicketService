package ticket.service.data;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * SeatId is a unique id for a seat.
 */
@Builder
@Getter
@ToString
@EqualsAndHashCode
public class SeatId {
    private final int rowNum;
    private final int seatNum;
}
