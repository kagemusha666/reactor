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

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Useful functions.
 */
@UtilityClass
public class Misc {

    /**
     * Convert a {@code Publisher<Void>} to a {@code Publisher<T>} allowing for type passthrough behavior.
     *
     * @param supplier a {@link Supplier} of a {@link Publisher} to execute
     * @param <T> the type passing through the flow
     * @return {@link Mono#empty()} of the appropriate type
     */
    <T> Mono<T> typeSafe(Supplier<Publisher<Void>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");

        return Flux.from(supplier.get()).then(Mono.empty());
    }

    /**
     * Execute the {@link Publisher} provided by a {@link Supplier} and propagate the error that initiated this behavior.
     * Typically used with {@link Flux#onErrorResume(Function)} and
     * {@link Mono#onErrorResume(Function)}.
     *
     * @param supplier a {@link Supplier} of a {@link Publisher} to execute when an error occurs
     * @param <T> the type passing through the flow
     * @return a {@link Mono#error(Throwable)} with the original error
     * @see Flux#onErrorResume(Function)
     * @see Mono#onErrorResume(Function)
     */
    <T> Function<Throwable, Mono<T>> appendError(Supplier<Publisher<?>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");

        return throwable -> Flux.from(supplier.get()).then(Mono.error(throwable));
    }

}
