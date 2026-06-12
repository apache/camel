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
package org.apache.camel.spi;

import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.support.service.ServiceSupport;
import org.jspecify.annotations.Nullable;

/**
 * Validates message content against a declared {@link DataType}, as described in the
 * <a href="https://camel.apache.org/manual/transformer.html">Transformer and Validator</a> documentation.
 * <p/>
 * When a route declares an {@code inputType} or {@code outputType} with {@code validate = true},
 * {@link org.apache.camel.processor.ContractAdvice} invokes the registered {@code Validator} for that type before (or
 * after) the route's main processing logic. If the message content does not satisfy the validator's rules, the
 * validator should throw a {@link org.apache.camel.ValidationException} to signal the failure.
 * <p/>
 * Validators are registered in the {@link ValidatorRegistry} keyed by their target {@link DataType}. Custom validators
 * extend this abstract class and are registered via the DSL (e.g., {@code inputType} / {@code outputType} with
 * {@code validate = true}), which wires them through {@link org.apache.camel.model.ValidatorDefinition} during route
 * startup.
 *
 * @see ValidatorRegistry
 * @see DataType
 * @see org.apache.camel.model.ValidatorDefinition
 */
public abstract class Validator extends ServiceSupport implements CamelContextAware {

    private @Nullable CamelContext camelContext;
    private @Nullable DataType type;

    /**
     * Perform data validation with specified type.
     *
     * @param  message             message to apply validation
     * @param  type                the data type
     * @throws ValidationException thrown if any validation error is detected
     */
    public abstract void validate(Message message, DataType type) throws ValidationException;

    /**
     * Get 'from' data type.
     */
    public @Nullable DataType getType() {
        return type;
    }

    /**
     * Set data type.
     *
     * @param type data type
     */
    public Validator setType(String type) {
        Objects.requireNonNull(type, "type");
        this.type = new DataType(type);
        return this;
    }

    @Override
    public @Nullable CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.camelContext = Objects.requireNonNull(context, "context");
    }

    @Override
    public String toString() {
        return String.format("%s[type='%s']", this.getClass().getSimpleName(), type);
    }

}
