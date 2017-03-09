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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.ComponentVerifier;
import org.apache.camel.util.ObjectHelper;

public final class ResultErrorBuilder {
    private String code;
    private String description;
    private Set<String> parameters;
    private Map<String, Object> attributes;

    public ResultErrorBuilder() {
    }

    // **********************************
    // Accessors
    // **********************************

    public ResultErrorBuilder code(String code) {
        this.code = code;
        return this;
    }

    public ResultErrorBuilder description(String description) {
        this.description = description;
        return this;
    }

    public ResultErrorBuilder parameter(String parameter) {
        if (parameter != null) {
            if (this.parameters == null) {
                this.parameters = new HashSet<>();
            }

            this.parameters.add(parameter);
        }
        return this;
    }

    public ResultErrorBuilder parameters(Collection<String> parameterList) {
        if (parameterList != null) {
            parameterList.forEach(this::parameter);
        }

        return this;
    }

    public ResultErrorBuilder attribute(String key, Object value) {
        if (value != null) {
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }

            this.attributes.put(key, value);
        }
        return this;
    }

    public <T> ResultErrorBuilder attribute(String key, Supplier<Optional<T>> supplier) {
        supplier.get().ifPresent(value -> attribute(key, value));
        return this;
    }

    // **********************************
    // Build
    // **********************************

    public ComponentVerifier.Error build() {
        return new DefaultResultError(
            code,
            description,
            parameters != null ? Collections.unmodifiableSet(parameters) : Collections.emptySet(),
            attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap()
        );
    }

    // **********************************
    // Helpers
    // **********************************

    public static ResultErrorBuilder withCode(String code) {
        return new ResultErrorBuilder().code(code);
    }

    public static ResultErrorBuilder withHttpCode(int code) {
        return withCode(Integer.toString(code))
            .attribute(ComponentVerifier.ERROR_TYPE_ATTRIBUTE, ComponentVerifier.ERROR_TYPE_HTTP)
            .attribute(ComponentVerifier.HTTP_CODE, code);
    }

    public static ResultErrorBuilder withHttpCodeAndText(int code, String text) {
        return withCodeAndDescription(Integer.toString(code), text)
            .attribute(ComponentVerifier.ERROR_TYPE_ATTRIBUTE, ComponentVerifier.ERROR_TYPE_HTTP)
            .attribute(ComponentVerifier.HTTP_CODE, code)
            .attribute(ComponentVerifier.HTTP_TEXT, text);
    }

    public static ResultErrorBuilder withCodeAndDescription(String code, String description) {
        return new ResultErrorBuilder().code(code).description(description);
    }

    public static ResultErrorBuilder withUnsupportedScope(String scope) {
        return new ResultErrorBuilder()
            .code(ComponentVerifier.CODE_UNSUPPORTED_SCOPE)
            .description("Unsupported scope: " + scope);
    }

    public static ResultErrorBuilder withException(Exception exception) {
        return new ResultErrorBuilder()
            .code(ComponentVerifier.CODE_EXCEPTION)
            .description(exception.getMessage())
            .attribute(ComponentVerifier.ERROR_TYPE_ATTRIBUTE, ComponentVerifier.ERROR_TYPE_EXCEPTION)
            .attribute(ComponentVerifier.EXCEPTION_INSTANCE, exception)
            .attribute(ComponentVerifier.EXCEPTION_CLASS, exception.getClass().getName());
    }

    public static ResultErrorBuilder withMissingOption(String optionName) {
        return new ResultErrorBuilder()
            .code(ComponentVerifier.CODE_MISSING_OPTION)
            .description(optionName + " should be set")
            .parameter(optionName);
    }

    public static ResultErrorBuilder withUnknownOption(String optionName) {
        return new ResultErrorBuilder()
            .code(ComponentVerifier.CODE_UNKNOWN_OPTION)
            .description("Unknown option " + optionName)
            .parameter(optionName);
    }

    public static ResultErrorBuilder withIllegalOption(String optionName) {
        return new ResultErrorBuilder()
            .code(ComponentVerifier.CODE_ILLEGAL_OPTION)
            .description("Illegal option " + optionName)
            .parameter(optionName);
    }

    public static ResultErrorBuilder withIllegalOption(String optionName, String optionValue) {
        return ObjectHelper.isNotEmpty(optionValue)
            ? new ResultErrorBuilder()
                .code(ComponentVerifier.CODE_ILLEGAL_OPTION_VALUE)
                .description(optionName + " has wrong value (" + optionValue + ")")
                .parameter(optionName)
            : withIllegalOption(optionName);
    }
}
