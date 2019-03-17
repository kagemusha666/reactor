package reactor;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private ConnectionFactory connectionFactory;

    public void start() {
        log.warn("creating psql connection...");

        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host("172.29.17.203")
                .username("postgres")
                .password("postgres")
                .database("set")
                .build());
    }

    public Mono<Connection> getConnection() {
        return obtainConnectionNonBlocking()
                .switchIfEmpty(obtainConnectionBlocking())
                .doOnNext(this::releaseConnection);
    }


    private Mono<Connection> obtainConnectionNonBlocking() {
        log.warn("try obtain connection...");
        if (establishedConnectionSemaphore.tryAcquire()) {
            log.warn("connection acquired at once");
            return Mono.justOrEmpty(establishedConnectionPool.poll())
                    .switchIfEmpty(Mono.from(connectionFactory.create()));
        } else {
            return Mono.empty();
        }
    }

    private Mono<Connection> obtainConnectionBlocking() {
        return Mono.create(this::waitForConnectionRelease)
                .subscribeOn(Schedulers.single())
                .publishOn(Schedulers.parallel());
    }

    private void waitForConnectionRelease(MonoSink<Connection> sink) {
        try {
            log.warn("waiting for connection...");
            if (establishedConnectionSemaphore.tryAcquire(CONNECTION_TIMEOUT, TimeUnit.SECONDS)) {
                log.warn("connection acquired");
                sink.success(establishedConnectionPool.poll());
            } else {
                log.warn("connection timed out");
                sink.error(new TimeoutException());
            }
        } catch (InterruptedException e) {
            sink.error(e);
        }
    }

    private void releaseConnection(Connection connection) {
        establishedConnectionPool.offer(connection);
        establishedConnectionSemaphore.release();
        log.warn("connection released");
    }

}
