/*
 *
 *  Copyright 2017 Christopher Pilsworth
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */
package io.github.resilience4j.retrofit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retrofit.internal.DecoratedCall;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

/**
 * Decorates a Retrofit {@link Call} to check with a {@link RateLimiter} if a call can be made.
 * Returns an error response with a HTTP 429 (too many requests) code and a message which indicates that the client
 * prevented the request.
 *
 * <p>
 * <code>
 * RetrofitRateLimiter.decorateCall(rateLimiter, call);
 * </code>
 */
public interface RetrofitRateLimiter {

    int ERROR_CODE = 429;
    String ERROR_MESSAGE = "Too many requests for the client";

    /**
     * Decorate {@link Call}s allow {@link CircuitBreaker} functionality.
     *
     * @param rateLimiter {@link RateLimiter} to apply
     * @param call        Call to decorate
     * @param <T>         Response type of call
     * @return Original Call decorated with CircuitBreaker
     */
    static <T> Call<T> decorateCall(final RateLimiter rateLimiter, final Call<T> call) {
        return new DecoratedCall<T>(call) {
            @Override
            public Response<T> execute() throws IOException {
                CheckedFunction0<Response<T>> restrictedSupplier = RateLimiter.decorateCheckedSupplier(rateLimiter, call::execute);
                final Try<Response<T>> response = Try.of(restrictedSupplier);
                return response.isSuccess() ? response.get() : handleFailure(response);
            }

            private Response<T> handleFailure(Try<Response<T>> response) throws IOException {
                try {
                    throw response.getCause();
                } catch (RequestNotPermitted | IllegalStateException e) {
                    return tooManyRequestsError();
                } catch (IOException ioe) {
                    throw ioe;
                } catch (Throwable t) {
                    throw new RuntimeException("Exception executing call", t);
                }
            }

            private Response<T> tooManyRequestsError() {
                return Response.error(ERROR_CODE, ResponseBody.create(MediaType.parse("text/plain"), ERROR_MESSAGE));
            }
        };
    }

}