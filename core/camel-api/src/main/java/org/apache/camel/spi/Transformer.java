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
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.jspecify.annotations.Nullable;

/**
 * Performs message body transformation between two {@link DataType}s as part of Camel's
 * <a href="https://camel.apache.org/manual/transformer.html">Transformer</a> contract mechanism.
 * <p/>
 * When a route declares an {@code inputType} or {@code outputType}, Camel's
 * {@link org.apache.camel.processor.ContractAdvice} inspects the current message's {@link DataType} and looks up a
 * matching {@code Transformer} in the {@link TransformerRegistry}. If the current type differs from the declared type,
 * the transformer's {@link #transform(org.apache.camel.Message, DataType, DataType)} method is called automatically,
 * without requiring the route developer to add an explicit conversion step.
 * <p/>
 * Transformers can be registered by annotating a subclass with {@link DataTypeTransformer} (the annotation drives
 * automatic registration) or manually via {@link org.apache.camel.CamelContext#getTransformerRegistry()}.
 *
 * @see DataTypeTransformer
 * @see TransformerRegistry
 * @see DataType
 */
public abstract class Transformer extends ServiceSupport implements CamelContextAware {

    private @Nullable CamelContext camelContext;
    private @Nullable String name;
    private @Nullable DataType from;
    private @Nullable DataType to;

    protected Transformer() {
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

    protected Transformer(String name) {
        this.name = Objects.requireNonNull(name, "name");
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
    public @Nullable String getName() {
        return name;
    }

    /**
     * Get 'from' data type.
     */
    public @Nullable DataType getFrom() {
        return from;
    }

    /**
     * Get 'to' data type.
     */
    public @Nullable DataType getTo() {
        return to;
    }

    /**
     * Set the name for this transformer. Usually a combination of scheme and name.
     */
    public Transformer setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
        return this;
    }

    /**
     * Set the scheme and/or name for this transformer. When using only a scheme, the transformer applies to all
     * transformations with that scheme.
     *
     * @param scheme supported data type scheme
     * @param name   transformer name
     */
    public Transformer setName(@Nullable String scheme, @Nullable String name) {
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
    public Transformer setFrom(@Nullable String from) {
        this.from = from != null ? new DataType(from) : null;
        return this;
    }

    /**
     * Set 'to' data type.
     */
    public Transformer setTo(@Nullable String to) {
        this.to = to != null ? new DataType(to) : null;
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
        return String.format("%s[name='%s', from='%s', to='%s']", this.getClass().getSimpleName(), name, from, to);
    }

}
