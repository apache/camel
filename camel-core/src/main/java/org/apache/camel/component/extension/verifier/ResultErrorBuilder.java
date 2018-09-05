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
package org.apache.camel.component.extension.verifier;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.component.extension.ComponentVerifierExtension.VerificationError;
import org.apache.camel.util.ObjectHelper;

public final class ResultErrorBuilder {
    private VerificationError.Code code;
    private String description;
    private Set<String> parameters;
    private Map<VerificationError.Attribute, Object> attributes;

    public ResultErrorBuilder() {
    }

    // **********************************
    // Accessors
    // **********************************

    public ResultErrorBuilder code(VerificationError.Code code) {
        this.code = code;
        return this;
    }

    public ResultErrorBuilder code(String code) {
        code(VerificationError.asCode(code));
        return this;
    }

    public ResultErrorBuilder description(String description) {
        this.description = description;
        return this;
    }

    public ResultErrorBuilder parameterKey(String parameter) {
        if (parameter != null) {
            if (this.parameters == null) {
                this.parameters = new HashSet<>();
            }

            this.parameters.add(parameter);
        }
        return this;
    }

    public ResultErrorBuilder parameterKeys(Collection<String> parameterList) {
        if (parameterList != null) {
            parameterList.forEach(this::parameterKey);
        }

        return this;
    }

    public ResultErrorBuilder detail(String key, Object value) {
        detail(VerificationError.asAttribute(key), value);
        return this;
    }

    public ResultErrorBuilder detail(VerificationError.Attribute key, Object value) {
        if (value != null) {
            if (this.attributes == null) {
                this.attributes = new HashMap<>();
            }

            this.attributes.put(key, value);
        }
        return this;
    }

    public <T> ResultErrorBuilder detail(String key, Supplier<Optional<T>> supplier) {
        detail(VerificationError.asAttribute(key), supplier);
        return this;
    }

    public <T> ResultErrorBuilder detail(VerificationError.Attribute key, Supplier<Optional<T>> supplier) {
        supplier.get().ifPresent(value -> detail(key, value));
        return this;
    }

    public ResultErrorBuilder details(Map<VerificationError.Attribute, Object> details) {
        for (Map.Entry<VerificationError.Attribute, Object> entry : details.entrySet()) {
            detail(entry.getKey(), entry.getValue());
        }

        return this;
    }

    // **********************************
    // Build
    // **********************************

    public VerificationError build() {
        return new DefaultResultVerificationError(
            code,
            description,
            parameters != null ? Collections.unmodifiableSet(parameters) : Collections.emptySet(),
            attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap()
        );
    }

    // **********************************
    // Helpers
    // **********************************

    public static ResultErrorBuilder fromError(VerificationError error) {
        return new ResultErrorBuilder()
            .code(error.getCode())
            .description(error.getDescription())
            .parameterKeys(error.getParameterKeys())
            .details(error.getDetails());
    }

    public static ResultErrorBuilder withCode(VerificationError.Code code) {
        return new ResultErrorBuilder().code(code);
    }

    public static ResultErrorBuilder withCode(String code) {
        return new ResultErrorBuilder().code(code);
    }

    public static ResultErrorBuilder withHttpCode(int code) {
        return withCode(convertHttpCodeToErrorCode(code))
            .detail(VerificationError.HttpAttribute.HTTP_CODE, code);
    }

    public static ResultErrorBuilder withHttpCodeAndText(int code, String text) {
        return withCodeAndDescription(convertHttpCodeToErrorCode(code), text)
            .detail(VerificationError.HttpAttribute.HTTP_CODE, code)
            .detail(VerificationError.HttpAttribute.HTTP_TEXT, text);
    }

    private static VerificationError.StandardCode convertHttpCodeToErrorCode(int code) {
        return code >= 400 && code < 500 ? VerificationError.StandardCode.AUTHENTICATION : VerificationError.StandardCode.GENERIC;
    }

    public static ResultErrorBuilder withCodeAndDescription(VerificationError.Code code, String description) {
        return new ResultErrorBuilder().code(code).description(description);
    }

    public static ResultErrorBuilder withUnsupportedScope(String scope) {
        return new ResultErrorBuilder()
            .code(VerificationError.StandardCode.UNSUPPORTED_SCOPE)
            .description("Unsupported scope: " + scope);
    }

    public static ResultErrorBuilder withUnsupportedComponent(String component) {
        return new ResultErrorBuilder()
            .code(VerificationError.StandardCode.UNSUPPORTED_COMPONENT)
            .description("Unsupported component: " + component);
    }

    public static ResultErrorBuilder withException(Exception exception) {
        return new ResultErrorBuilder()
            .code(VerificationError.StandardCode.EXCEPTION)
            .description(exception.getMessage())
            .detail(VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE, exception)
            .detail(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, exception.getClass().getName());
    }

    public static ResultErrorBuilder withMissingOption(String optionName) {
        return new ResultErrorBuilder()
            .code(VerificationError.StandardCode.MISSING_PARAMETER)
            .description(optionName + " should be set")
            .parameterKey(optionName);
    }

    public static ResultErrorBuilder withUnknownOption(String optionName) {
        return new ResultErrorBuilder()
            .code(VerificationError.StandardCode.UNKNOWN_PARAMETER)
            .description("Unknown option " + optionName)
            .parameterKey(optionName);
    }

    public static ResultErrorBuilder withIllegalOption(String optionName) {
        return new ResultErrorBuilder()
            .code(VerificationError.StandardCode.ILLEGAL_PARAMETER)
            .description("Illegal option " + optionName)
            .parameterKey(optionName);
    }

    public static ResultErrorBuilder withIllegalOption(String optionName, String optionValue) {
        return ObjectHelper.isNotEmpty(optionValue)
            ? new ResultErrorBuilder()
                .code(VerificationError.StandardCode.ILLEGAL_PARAMETER_VALUE)
                .description(optionName + " has wrong value (" + optionValue + ")")
                .parameterKey(optionName)
            : withIllegalOption(optionName);
    }
}
