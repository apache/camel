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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.processor.BaseProcessorSupport;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.TransformerKey;

/**
 * Processor that applies data type conversion by reading the target data type from the exchange property
 * {@code CamelDataType} at runtime. Unlike {@link DataTypeProcessor}, this processor does not require fromType and
 * toType to be set at initialization time, allowing for dynamic data type resolution based on exchange properties.
 * <p>
 * This is particularly useful in Kamelets where the data type is determined by exchange properties rather than static
 * configuration.
 */
public class DelegatingDataTypeProcessor extends BaseProcessorSupport implements CamelContextAware {

    private static final String CAMEL_DATA_TYPE_PROPERTY = "CamelDataType";

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getMessage();

        try {
            // Read the target data type from the exchange property
            String toTypeStr = exchange.getProperty(CAMEL_DATA_TYPE_PROPERTY, String.class);
            if (toTypeStr == null) {
                exchange.setException(new IllegalArgumentException(
                        "Exchange property '" + CAMEL_DATA_TYPE_PROPERTY + "' is not set. "
                                                                   + "Cannot determine target data type for transformation."));
                callback.done(true);
                return true;
            }

            DataType toDataType = new DataType(toTypeStr);
            DataType fromDataType = DataType.ANY;

            // Try to get the source data type from the message
            if (message instanceof DataTypeAware dta && dta.hasDataType()) {
                fromDataType = dta.getDataType();
            }

            // Resolve the transformer at runtime
            Transformer transformer = getCamelContext().getTransformerRegistry()
                    .resolveTransformer(new TransformerKey(fromDataType, toDataType));

            if (transformer == null) {
                exchange.setException(new IllegalArgumentException(
                        "Cannot resolve transformer from: " + fromDataType + " to: " + toDataType));
            } else {
                transformer.transform(message, fromDataType, toDataType);
            }
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(true);
        }

        return true;
    }
}
