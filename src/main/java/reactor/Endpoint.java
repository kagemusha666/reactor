package reactor;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;


/**
 * Server end point.
 */
@Slf4j
public class Endpoint
{
    public static void main(String[] args)
    {
        RouterFunction<ServerResponse> route = route()
                .GET("/spring/{param}", RequestPredicates.accept(MediaType.TEXT_PLAIN), Endpoint::getSpringResponse)
                .build();

        HttpHandler handler = RouterFunctions.toHttpHandler(route);
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(handler);

        DisposableServer server = HttpServer.create()
                .port(8080)
                .handle(adapter)
                .wiretap(true)
                .bindNow();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.warn("Interrupted {}", e);
        } finally {
            server.disposeNow();
        }
    }

    private static Mono<ServerResponse> getSpringResponse(ServerRequest request) {
        String message = String.format("Hello, %s!", request.pathVariable("param"));
        return ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).body(Mono.just(message), String.class);
    }

}
