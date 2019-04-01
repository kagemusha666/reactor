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

import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Wrapper for {@link io.r2dbc.spi.Connection}
 */
public class Connection {

    private Database database;
    private io.r2dbc.spi.Connection delegate;

    private Connection(Database database, io.r2dbc.spi.Connection delegate) {
        this.database = database;
        this.delegate = delegate;
    }

    public static Mono<Connection> create(Database database, ConnectionFactory factory) {
        return Mono.from(factory.create()).map(delegate -> new Connection(database, delegate));
    }

    public Publisher<? extends Result> inTransaction(Function<io.r2dbc.spi.Connection, Publisher<? extends  Result>> handler) {
        return Mono.from(delegate.beginTransaction())
                .thenMany(handler.apply(delegate))
                .concatWith(Misc.typeSafe(delegate::commitTransaction))
                .onErrorResume(Misc.appendError(delegate::rollbackTransaction))
                .doFinally(signalType -> database.releaseConnection(this));
    }

}
