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
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * <a href="http://camel.apache.org/transformer.html">Transformer</a> performs message transformation according to the
 * declared data type. {@link org.apache.camel.processor.ContractAdvice} looks for a required Transformer and apply if
 * input/output type declared on a route is different from current message type.
 *
 * @see {@link org.apache.camel.processor.ContractAdvice} {@link DataType}
 *      {@link org.apache.camel.model.InputTypeDefinition} {@link org.apache.camel.model.OutputTypeDefinition}
 */
public abstract class Transformer extends ServiceSupport implements CamelContextAware {

    private CamelContext camelContext;
    private String name;
    private DataType from;
    private DataType to;

    public Transformer() {
        if (this.getClass().isAnnotationPresent(DataTypeTransformer.class)) {
            DataTypeTransformer annotation = this.getClass().getAnnotation(DataTypeTransformer.class);
            if (ObjectHelper.isNotEmpty(annotation.name())) {
                this.name = annotation.name();
            }

            if (ObjectHelper.isNotEmpty(annotation.fromType())) {
                this.from = new DataType(annotation.fromType());
            }

            if (ObjectHelper.isNotEmpty(annotation.toType())) {
                this.to = new DataType(annotation.toType());
            }
        }
    }

    public Transformer(String name) {
        this.name = name;
    }

    /**
     * Perform data transformation with specified from/to type.
     *
     * @param message message to apply transformation
     * @param from    'from' data type
     * @param to      'to' data type
     */
    public abstract void transform(Message message, DataType from, DataType to) throws Exception;

    /**
     * Get the transformer name that represents the supported data type model.
     */
    public String getName() {
        return name;
    }

    /**
     * Get 'from' data type.
     */
    public DataType getFrom() {
        return from;
    }

    /**
     * Get 'to' data type.
     */
    public DataType getTo() {
        return to;
    }

    /**
     * Set the name for this transformer. Usually a combination of scheme and name.
     */
    public Transformer setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the scheme and/or name for this transformer. When using only a scheme, the transformer applies to all
     * transformations with that scheme.
     *
     * @param scheme supported data type scheme
     * @param name   transformer name
     */
    public Transformer setName(String scheme, String name) {
        if (ObjectHelper.isNotEmpty(scheme)) {
            if (ObjectHelper.isNotEmpty(name)) {
                this.name = scheme + ":" + name;
            } else {
                this.name = scheme;
            }
        } else {
            this.name = name;
        }
        return this;
    }

    /**
     * Set 'from' data type.
     */
    public Transformer setFrom(String from) {
        this.from = new DataType(from);
        return this;
    }

    /**
     * Set 'to' data type.
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
        return String.format("%s[name='%s', from='%s', to='%s']", this.getClass().getSimpleName(), name, from, to);
    }

}
