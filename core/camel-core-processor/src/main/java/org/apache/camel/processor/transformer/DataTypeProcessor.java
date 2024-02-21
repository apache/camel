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

package org.apache.camel.processor.transformer;

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.engine.TransformerKey;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor applies data type conversion based on given format name. Searches for matching data type transformer in
 * context and applies its logic.
 */
public class DataTypeProcessor implements Processor {

    public static final String DATA_TYPE_PROPERTY = "CamelDataType";

    private static final Logger LOG = LoggerFactory.getLogger(DataTypeProcessor.class);

    private String fromType;
    private String toType;

    private boolean ignoreMissingDataType;

    private Transformer transformer;

    public DataTypeProcessor() {
    }

    public DataTypeProcessor(String fromType, String toType) {
        this.fromType = fromType;
        this.toType = toType;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (toType == null && exchange.hasProperties() && exchange.getProperties().containsKey(DATA_TYPE_PROPERTY)) {
            toType = exchange.getProperty(DATA_TYPE_PROPERTY, String.class);
        }

        if (toType == null || toType.isEmpty()) {
            return;
        }

        Message message = exchange.getMessage();

        DataType toDataType = new DataType(toType);
        DataType fromDataType = DataType.ANY;
        if (fromType != null) {
            fromDataType = new DataType(fromType);
        } else if (message instanceof DataTypeAware && ((DataTypeAware) message).hasDataType()) {
            fromDataType = ((DataTypeAware) message).getDataType();
        }

        Optional<Transformer> dataTypeTransformer = doLookupTransformer(exchange.getContext(), fromDataType, toDataType);
        if (dataTypeTransformer.isPresent()) {
            dataTypeTransformer.get().transform(message, fromDataType, toDataType);
        } else if (ignoreMissingDataType) {
            LOG.debug("Unable to find  data type transformer from {} to type {}", fromDataType, toDataType);
        } else {
            throw new CamelExecutionException(
                    String.format("Missing data type transformer from %s to type %s", fromDataType, toDataType), exchange);
        }
    }

    private Optional<Transformer> doLookupTransformer(CamelContext context, DataType fromType, DataType toType) {
        if (transformer != null) {
            return Optional.of(transformer);
        }

        Transformer maybeTransformer
                = context.getTransformerRegistry().resolveTransformer(new TransformerKey(fromType, toType));
        if (maybeTransformer != null) {
            this.transformer = maybeTransformer;
            return Optional.of(maybeTransformer);
        }

        return Optional.empty();
    }

    public void setFromType(String fromType) {
        this.fromType = fromType;
    }

    public void setToType(String toType) {
        this.toType = toType;
    }

    public void setTransformer(Transformer transformer) {
        this.transformer = transformer;
    }

    public void setIgnoreMissingDataType(boolean ignoreMissingDataType) {
        this.ignoreMissingDataType = ignoreMissingDataType;
    }
}
