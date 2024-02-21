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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Message;
import org.apache.camel.ValidationException;
import org.apache.camel.support.service.ServiceSupport;

/**
 * <a href="http://camel.apache.org/validator.html">Validator</a> performs message content validation according to the
 * declared data type. {@link org.apache.camel.processor.ContractAdvice} applies Validator if input/output type is
 * declared on a route with validation enabled.
 *
 * @see {@link org.apache.camel.processor.ContractAdvice} {@link org.apache.camel.model.InputTypeDefinition}
 *      {@link org.apache.camel.model.OutputTypeDefinition}
 */
public abstract class Validator extends ServiceSupport implements CamelContextAware {

    private CamelContext camelContext;
    private DataType type;

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
    public DataType getType() {
        return type;
    }

    /**
     * Set data type.
     *
     * @param type data type
     */
    public Validator setType(String type) {
        this.type = new DataType(type);
        return this;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.camelContext = context;
    }

    @Override
    public String toString() {
        return String.format("%s[type='%s']", this.getClass().getSimpleName(), type);
    }

}
