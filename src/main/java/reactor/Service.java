package reactor;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;


/**
 * Router handler.
 */
@AllArgsConstructor
public class Service {

    private final Database database;


    public Mono<ServerResponse> getClient(ServerRequest request) {
        final var id = Integer.valueOf(request.pathVariable("id"));

        var client = database.getConnection()
                .switchIfEmpty(Mono.error(IllegalArgumentException::new))
                .flatMapMany(conn -> conn.createStatement("SELECT firstname, lastname FROM card_clients WHERE guid = $1")
                        .bind("$1", id)
                        .execute())
                .flatMap(result -> result.map(Service::mapper))
                .singleOrEmpty();

        return client.flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(client, Client.class))
                .switchIfEmpty(ServerResponse.noContent().build())
                .onErrorResume(TimeoutException.class::isInstance,
                        throwable -> ServerResponse.status(HttpStatus.REQUEST_TIMEOUT).build());
    }


    private static Client mapper(Row row, RowMetadata meta) {
        return new Client(row.get("firstname", String.class),
                row.get("lastname", String.class));
    }

}
