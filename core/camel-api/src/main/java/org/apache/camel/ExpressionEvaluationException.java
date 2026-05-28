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
package org.apache.camel;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * An exception thrown if evaluation of the expression failed.
 */
public class ExpressionEvaluationException extends RuntimeCamelException {

    private final transient @Nullable Expression expression;
    private final transient @Nullable Exchange exchange;

    /**
     * @param expression the expression that failed to evaluate
     * @param exchange   the exchange that caused the error
     * @param cause      the cause of the failure
     */
    public ExpressionEvaluationException(
                                         @Nullable Expression expression, @Nullable Exchange exchange,
                                         @Nullable Throwable cause) {
        super(cause);
        this.expression = expression;
        this.exchange = exchange;
    }

    /**
     * @param expression the expression that failed to evaluate
     * @param message    the detail message
     * @param exchange   the exchange that caused the error
     * @param cause      the cause of the failure
     */
    public ExpressionEvaluationException(
                                         @Nullable Expression expression, String message, @Nullable Exchange exchange,
                                         @Nullable Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.expression = expression;
        this.exchange = exchange;
    }

    public @Nullable Expression getExpression() {
        return expression;
    }

    public @Nullable Exchange getExchange() {
        return exchange;
    }
}
