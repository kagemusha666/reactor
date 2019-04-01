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

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;

/**
 * Database connection pool.
 */
@Slf4j
public class Database {

    static final int KEEP_ALIVE_CONNECTIONS = 4;
    static final int CONNECTION_TIMEOUT = 3; // seconds

    private final Semaphore establishedConnectionSemaphore = new Semaphore(KEEP_ALIVE_CONNECTIONS);
    private final ConcurrentLinkedQueue<Connection> establishedConnectionPool = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean connectionTimedOut = new AtomicBoolean(true);

    private ConnectionFactory connectionFactory;

    public void start() {
        log.warn("creating psql connection...");

        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host("172.29.17.40")
                .username("postgres")
                .password("postgres")
                .database("set")
                .build());

        for (int i = 0; i < KEEP_ALIVE_CONNECTIONS; ++i) {
            establishedConnectionPool.add(Connection.create(this, connectionFactory).block());
        }
    }

    public Mono<Connection> getConnection() {
        return obtainConnectionNonBlocking()
                .switchIfEmpty(obtainConnectionBlocking());
    }


    private Mono<Connection> obtainConnectionNonBlocking() {
        return Mono.create(this::getConnectionAtOnce);
    }

    private Mono<Connection> obtainConnectionBlocking() {
        return Mono.create(this::waitForConnectionRelease)
                .subscribeOn(Schedulers.elastic());
    }

    private void getConnectionAtOnce(MonoSink<Connection> sink) {
        if (establishedConnectionSemaphore.tryAcquire()) {
            Connection connection = establishedConnectionPool.poll();
            sink.success(connection);
        } else {
            sink.success();
        }
    }

    private void waitForConnectionRelease(MonoSink<Connection> sink) {
        try {
            if (establishedConnectionSemaphore.tryAcquire() ||
                    (/*connectionTimedOut.get() &&*/establishedConnectionSemaphore.tryAcquire(CONNECTION_TIMEOUT, TimeUnit.SECONDS))) {
                log.warn("connection got!");
                connectionTimedOut.compareAndSet(false, true);
                sink.success(establishedConnectionPool.poll());
            } else {
                log.warn("connection timed out");
                connectionTimedOut.compareAndSet(true, false);
                sink.error(new TimeoutException());
            }
        } catch (InterruptedException e) {
            sink.error(e);
        }
    }

    void releaseConnection(Connection connection) {
        establishedConnectionPool.offer(connection);
        establishedConnectionSemaphore.release();
    }

}
