package reactor;

import lombok.AllArgsConstructor;
import lombok.Value;


/**
 * Data conversion.
 */
@Value
@AllArgsConstructor
public class Client {
    String firstName;
    String lastName;
}
