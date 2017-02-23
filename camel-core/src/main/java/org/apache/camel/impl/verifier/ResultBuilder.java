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
package org.apache.camel.impl.verifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.camel.ComponentVerifier;
import org.apache.camel.IllegalOptionException;
import org.apache.camel.NoSuchOptionException;
import org.apache.camel.util.function.ThrowingBiConsumer;
import org.apache.camel.util.function.ThrowingConsumer;

public final class ResultBuilder {
    private Optional<ComponentVerifier.Scope> scope;
    private Optional<ComponentVerifier.Result.Status> status;
    private List<ComponentVerifier.Error> errors;

    public ResultBuilder() {
        this.scope = Optional.empty();
        this.status = scope.empty();
    }

    // **********************************
    // Accessors
    // **********************************

    public ResultBuilder scope(ComponentVerifier.Scope scope) {
        this.scope = Optional.of(scope);
        return this;
    }

    public ResultBuilder status(ComponentVerifier.Result.Status status) {
        this.status = Optional.of(status);
        return this;
    }

    public ResultBuilder error(ComponentVerifier.Error error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }

        this.errors.add(error);
        this.status = Optional.of(ComponentVerifier.Result.Status.ERROR);

        return this;
    }

    public ResultBuilder error(Optional<ComponentVerifier.Error> error) {
        error.ifPresent(e -> error(e));
        return this;
    }

    public ResultBuilder error(Supplier<Optional<ComponentVerifier.Error>> supplier) {
        return error(supplier.get());
    }

    public ResultBuilder error(ThrowingConsumer<ResultBuilder, Exception> consumer) {
        try {
            consumer.accept(this);
        } catch (NoSuchOptionException e) {
            error(ResultErrorBuilder.withMissingOption(e.getOptionName()).build());
        } catch (IllegalOptionException e) {
            error(ResultErrorBuilder.withIllegalOption(e.getOptionName(), e.getOptionValue()).build());
        } catch (Exception e) {
            error(ResultErrorBuilder.withException(e).build());
        }

        return this;
    }

    public <T> ResultBuilder error(T data, ThrowingBiConsumer<ResultBuilder, T, Exception> consumer) {
        try {
            consumer.accept(this, data);
        } catch (NoSuchOptionException e) {
            error(ResultErrorBuilder.withMissingOption(e.getOptionName()).build());
        } catch (IllegalOptionException e) {
            error(ResultErrorBuilder.withIllegalOption(e.getOptionName(), e.getOptionValue()).build());
        } catch (Exception e) {
            error(ResultErrorBuilder.withException(e).build());
        }

        return this;
    }

    // **********************************
    // Build
    // **********************************

    public ComponentVerifier.Result build() {
        return new DefaultResult(
            scope.orElseGet(() -> ComponentVerifier.Scope.NONE),
            status.orElseGet(() -> ComponentVerifier.Result.Status.UNSUPPORTED),
            errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList()
        );
    }

    // **********************************
    // Helpers
    // **********************************

    public static ResultBuilder withStatus(ComponentVerifier.Result.Status status) {
        return new ResultBuilder().status(status);
    }

    public static ResultBuilder withStatusAndScope(ComponentVerifier.Result.Status status, ComponentVerifier.Scope scope) {
        return new ResultBuilder().status(status).scope(scope);
    }

    public static ResultBuilder withScope(ComponentVerifier.Scope scope) {
        return new ResultBuilder().scope(scope);
    }

    public static ResultBuilder unsupported() {
        return withStatusAndScope(ComponentVerifier.Result.Status.UNSUPPORTED, ComponentVerifier.Scope.NONE);
    }
}
