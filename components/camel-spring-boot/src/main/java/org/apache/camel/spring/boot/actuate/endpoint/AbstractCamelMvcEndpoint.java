/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.boot.actuate.endpoint;

import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Adapter to expose {@link T} as an {@link MvcEndpoint}.
 */
abstract class AbstractCamelMvcEndpoint<T extends Endpoint> extends EndpointMvcAdapter {
    private final T delegate;

    protected AbstractCamelMvcEndpoint(String path, T delegate) {
        super(delegate);
        this.delegate = delegate;

        setPath(path);
    }

    // ********************************************
    // Helpers
    // ********************************************

    protected T delegate() {
        return this.delegate;
    }

    protected Object doIfEnabled(Supplier<Object> supplier) {
        if (!delegate.isEnabled()) {
            return getDisabledResponse();
        }

        return supplier.get();
    }

    protected Object doIfEnabled(Function<T, Object> supplier) {
        if (!delegate.isEnabled()) {
            return getDisabledResponse();
        }

        return supplier.apply(delegate);
    }

    @SuppressWarnings("serial")
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public static class GenericException extends RuntimeException {
        public GenericException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
