package reactor;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.netty.http.server.HttpServer;


/**
 * Server end point.
 */
@Slf4j
public class Endpoint
{

    public static void main(String[] args)
    {
        var database = new Database();
        database.start();

        var service = new Service(database);

        var route = route()
                .GET("/client/{id}", service::getClient)
                .build();

        var handler = RouterFunctions.toHttpHandler(route);
        var adapter = new ReactorHttpHandlerAdapter(handler);

        log.warn("starting server...");

        HttpServer.create()
                .port(8080)
                .handle(adapter)
                .wiretap(true)
                .bindUntilJavaShutdown(Duration.ZERO, s -> log.warn("server started..."));
    }

}
