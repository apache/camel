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
import java.util.Objects;
import java.util.Optional;

import org.apache.camel.Builder;
import org.apache.camel.util.ObjectHelper;
import org.jspecify.annotations.Nullable;

/**
 * A builder helper to create a {@link HealthCheck} result.
 */
public final class HealthCheckResultBuilder implements Builder<HealthCheck.Result> {

    private final HealthCheck check;
    private @Nullable String message;
    private @Nullable Throwable error;
    private @Nullable Map<String, Object> details;
    private HealthCheck.@Nullable State state;

    private HealthCheckResultBuilder(HealthCheck check) {
        this.check = Objects.requireNonNull(check, "check");
    }

    public @Nullable String message() {
        return this.message;
    }

    public HealthCheckResultBuilder message(@Nullable String message) {
        this.message = message;
        return this;
    }

    public @Nullable Throwable error() {
        return this.error;
    }

    public HealthCheckResultBuilder error(@Nullable Throwable error) {
        this.error = error;
        return this;
    }

    public @Nullable Object detail(String key) {
        Objects.requireNonNull(key, "key");
        return this.details != null ? this.details.get(key) : null;
    }

    public HealthCheckResultBuilder detail(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (this.details == null) {
            this.details = new HashMap<>();
        }

        this.details.put(key, value);
        return this;
    }

    public HealthCheckResultBuilder details(Map<String, Object> details) {
        Objects.requireNonNull(details, "details");
        if (ObjectHelper.isNotEmpty(details)) {
            details.forEach(this::detail);
        }

        return this;
    }

    public HealthCheck.@Nullable State state() {
        return this.state;
    }

    public HealthCheckResultBuilder state(HealthCheck.State state) {
        this.state = Objects.requireNonNull(state, "state");
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

        final HealthCheck.State responseState = Objects.requireNonNull(this.state, "state");
        final Optional<String> responseMessage = Optional.ofNullable(this.message);
        final Optional<Throwable> responseError = Optional.ofNullable(this.error);
        final Map<String, Object> responseDetails = ObjectHelper.isNotEmpty(this.details)
                ? Collections.unmodifiableMap(new HashMap<>(this.details))
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
        Objects.requireNonNull(check, "check");
        return new HealthCheckResultBuilder(check);
    }

    @Override
    public String toString() {
        return "HealthCheck[" + check.getGroup() + "/" + check.getId() + "]";
    }
}
