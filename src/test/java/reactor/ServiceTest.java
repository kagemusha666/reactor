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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Testing exception handling.
 */
@Slf4j
class ServiceTest implements ReactorDebugMode {

    Service service;
    Database database;

    @BeforeEach
    void setDatabaseAndInitializeService() {
        database = mock(Database.class);
        service = new Service(database);
    }

    @AfterEach
    void resetDatabase() {
        reset(database);
    }

    @Test
    void timedOutException() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.pathVariable(eq("id"))).thenReturn("666");

        when(database.getConnection()).thenReturn(Mono.error(TimeoutException::new));

        var response = service.getClient(request);

        StepVerifier.create(response)
                .expectNextMatches(r -> r.statusCode().equals(HttpStatus.REQUEST_TIMEOUT))
                .verifyComplete();
    }

    @Test
    void otherException() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.pathVariable(eq("id"))).thenReturn("666");

        when(database.getConnection()).thenReturn(Mono.error(RuntimeException::new));

        var response = service.getClient(request);

        StepVerifier.create(response)
                .verifyError(RuntimeException.class);
    }

    @Test
    void emptyConnection() {
        ServerRequest request = mock(ServerRequest.class);
        when(request.pathVariable(eq("id"))).thenReturn("666");

        when(database.getConnection()).thenReturn(Mono.empty());

        var response = service.getClient(request);

        StepVerifier.create(response)
                .verifyError();
    }

}