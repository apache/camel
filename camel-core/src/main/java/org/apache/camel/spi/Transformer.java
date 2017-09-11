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
package org.apache.camel.spi;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Message;
import org.apache.camel.model.InputTypeDefinition;
import org.apache.camel.model.OutputTypeDefinition;
import org.apache.camel.processor.ContractAdvice;
import org.apache.camel.support.ServiceSupport;

/**
 * <a href="http://camel.apache.org/transformer.html">Transformer</a>
 * performs message transformation according to the declared data type.
 * {@link ContractAdvice} looks for a required Transformer and apply if
 * input/output type declared on a route is different from current message type.
 *  
 * @see {@link ContractAdvice}
 * {@link DataType} {@link InputTypeDefinition} {@link OutputTypeDefinition}
 */
public abstract class Transformer extends ServiceSupport implements CamelContextAware {

    private CamelContext camelContext;
    private String model;
    private DataType from;
    private DataType to;

    /**
     * Perform data transformation with specified from/to type.
     *
     * @param message message to apply transformation
     * @param from 'from' data type
     * @param to 'to' data type
     */
    public abstract void transform(Message message, DataType from, DataType to) throws Exception;

    /**
     * Get a data model which is supported by this transformer.
     */
    public String getModel() {
        return model;
    };

    /**
     * Get 'from' data type.
     */
    public DataType getFrom() {
        return from;
    };

    /**
     * Get 'to' data type.
     */
    public DataType getTo() {
        return to;
    };

    /**
     * Set data model.
     *
     * @param model data model
     */
    public Transformer setModel(String model) {
        this.model = model;
        return this;
    }

    /**
     * Set 'from' data type.
     *
     * @param from 'from' data type
     */
    public Transformer setFrom(String from) {
        this.from = new DataType(from);
        return this;
    }

    /**
     * Set 'to' data type.
     *
     * @param to 'to' data type
     */
    public Transformer setTo(String to) {
        this.to = new DataType(to);
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
        return String.format("%s[scheme='%s', from='%s', to='%s']", this.getClass().getSimpleName(), model, from, to);
    }

    @Override
    protected void doStart() throws Exception {
        // no-op
    }

    @Override
    protected void doStop() throws Exception {
        // no-op
    }
}
