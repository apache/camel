/*
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
package org.apache.camel.health;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Builder;
import org.apache.camel.util.ObjectHelper;

/**
 * A builder helper to create a result.
 */
public final class HealthCheckResultBuilder implements Builder<HealthCheck.Result> {
    private HealthCheck check;
    private String message;
    private Throwable error;
    private Map<String, Object> details;
    private HealthCheck.State state;

    private HealthCheckResultBuilder(HealthCheck check) {
        this.check = check;
    }

    public String message() {
        return this.message;
    }

    public HealthCheckResultBuilder message(String message) {
        this.message = message;
        return this;
    }

    public Throwable error() {
        return this.error;
    }

    public HealthCheckResultBuilder error(Throwable error) {
        this.error = error;
        return this;
    }

    public Object detail(String key) {
        return this.details != null ? this.details.get(key) : null;
    }

    public HealthCheckResultBuilder detail(String key, Object value) {
        if (this.details == null) {
            this.details = new HashMap<>();
        }

        this.details.put(key, value);
        return this;
    }

    public HealthCheckResultBuilder details(Map<String, Object> details) {
        if (ObjectHelper.isNotEmpty(details)) {
            details.forEach(this::detail);
        }

        return this;
    }

    public HealthCheck.State state() {
        return this.state;
    }

    public HealthCheckResultBuilder state(HealthCheck.State state) {
        this.state = state;
        return this;
    }

    public HealthCheckResultBuilder up() {
        return state(HealthCheck.State.UP);
    }

    public HealthCheckResultBuilder down() {
        return state(HealthCheck.State.DOWN);
    }

    public HealthCheckResultBuilder unknown() {
        return state(HealthCheck.State.UNKNOWN);
    }

    @Override
    public HealthCheck.Result build() {
        // Validation
        ObjectHelper.notNull(this.state, "Response State");

        final HealthCheck.State responseState = this.state;
        final Optional<String> responseMessage = Optional.ofNullable(this.message);
        final Optional<Throwable> responseError = Optional.ofNullable(this.error);
        final Map<String, Object> responseDetails = HealthCheckResultBuilder.this.details != null
            ? Collections.unmodifiableMap(new HashMap<>(HealthCheckResultBuilder.this.details))
            : Collections.emptyMap();

        return new HealthCheck.Result() {
            @Override
            public HealthCheck getCheck() {
                return check;
            }

            @Override
            public HealthCheck.State getState() {
                return responseState;
            }

            @Override
            public Optional<String> getMessage() {
                return responseMessage;
            }

            @Override
            public Optional<Throwable> getError() {
                return responseError;
            }

            @Override
            public Map<String, Object> getDetails() {
                return responseDetails;
            }
        };
    }

    public static HealthCheckResultBuilder on(HealthCheck check) {
        return new HealthCheckResultBuilder(check);
    }
}
