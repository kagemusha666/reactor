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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.util.reflection.Whitebox.setInternalState;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Test connection starvation state.
 */
@Slf4j
class DatabaseTest implements ReactorDebugMode {

    Database database = new Database();
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);


    @BeforeEach
    void setupConnectionFactory() {
        setInternalState(database, "connectionFactory", connectionFactory);
    }

    @Test
    void simpleConnection() {
        Connection connection = mock(Connection.class);
        doReturn(Mono.just(connection)).when(connectionFactory).create();

        StepVerifier.create(database.getConnection())
                .expectNextMatches(Connection.class::isInstance)
                .verifyComplete();

        verify(connection, never()).close();
    }

    @Test
    void connectionTimedOut() {
        doReturn(Mono.just(mock(Connection.class))).when(connectionFactory).create();

        IntStream.range(0, Database.KEEP_ALIVE_CONNECTIONS)
                .forEach(i -> database.getConnection());

        StepVerifier.create(database.getConnection())
                .verifyError(TimeoutException.class);
    }

    @Test
    void connectionBlock() {
        doReturn(Mono.just(mock(Connection.class))).when(connectionFactory).create();

        IntStream.range(0, Database.KEEP_ALIVE_CONNECTIONS)
                .forEach(i -> schedule(3000, database.getConnection()::subscribe));

        StepVerifier.create(database.getConnection())
                .expectNextMatches(Connection.class::isInstance)
                .verifyComplete();
    }

    @Test
    @Disabled("Too much time to process")
    void connectionBlockSeveral() throws InterruptedException {
        doAnswer(this::createConnection).when(connectionFactory).create();

        IntStream.range(0, Database.KEEP_ALIVE_CONNECTIONS)
                .forEach(i -> schedule(3000, database.getConnection()::subscribe));

        AtomicInteger counter = new AtomicInteger(Database.KEEP_ALIVE_CONNECTIONS * 2);
        IntStream.range(0, counter.get()).mapToObj(i -> database.getConnection())
                .forEach(mono -> mono.subscribe(connection1 -> {
                    counter.decrementAndGet();
                }));

        while (counter.get() > 0) {
            Thread.sleep(10);
        }
    }


    Mono<Connection> createConnection(InvocationOnMock unused) {
        return Mono.just(mock(Connection.class));
    }

    void schedule(long delay, Runnable runnable) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }
        }, delay);
    }

}