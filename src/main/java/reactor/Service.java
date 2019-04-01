/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org>
 */

package reactor;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Router handler.
 */
@AllArgsConstructor
@Slf4j
public class Service {

    private final Database database;


    public Mono<ServerResponse> getClient(ServerRequest request) {
        final var id = Integer.valueOf(request.pathVariable("id"));

        var client = database.getConnection()
                .switchIfEmpty(Mono.error(IllegalArgumentException::new))
                .flatMapMany(conn1 -> conn1.inTransaction(
                        conn2 -> conn2.createStatement("SELECT pg_sleep(0.04), firstname, lastname FROM card_clients WHERE guid = $1")
                                .bind("$1", id)
                                .execute()
                ))
                .flatMap(result -> result.map(Service::mapper))
                .singleOrEmpty();

        return client.flatMap(data -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(Mono.just(data), Client.class))
                .switchIfEmpty(ServerResponse.noContent().build())
                .onErrorResume(TimeoutException.class::isInstance,
                        throwable -> ServerResponse.status(HttpStatus.REQUEST_TIMEOUT).build());
    }


    private static Client mapper(Row row, RowMetadata meta) {
        try {
            return new Client(row.get("firstname", String.class),
                    row.get("lastname", String.class));
        } catch (Exception e) {
            log.error("{}", e);
            return Client.NullClient();
        }
    }

    public Mono<ServerResponse> getAllClients(ServerRequest request) {
        var clients = database.getConnection()
                .switchIfEmpty(Mono.error(IllegalArgumentException::new))
                .flatMapMany(conn1 -> conn1.inTransaction(
                        conn2 -> conn2.createStatement("SELECT firstname, lastname FROM card_clients")
                                .execute()
                ))
                .flatMap(result -> result.map(Service::mapper))
                .filter(Client::isNotNull);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_STREAM_JSON)
                .body(BodyInserters.fromPublisher(clients, Client.class));
    }

}
