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
 * Processor applies data type conversion based on given format name. Searches for matching data type transformer in
 * context and applies its logic.
 */
public class DataTypeProcessor extends BaseProcessorSupport implements CamelContextAware {

    private CamelContext camelContext;
    private String fromType;
    private String toType;

    private Transformer transformer;
    private DataType fromDataType;
    private DataType toDataType;

    public DataTypeProcessor() {
    }

    public DataTypeProcessor(String fromType, String toType) {
        this.fromType = fromType;
        this.toType = toType;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        toDataType = new DataType(toType);
        fromDataType = DataType.ANY;
        if (fromType != null) {
            fromDataType = new DataType(fromType);
        }

        transformer
                = getCamelContext().getTransformerRegistry().resolveTransformer(new TransformerKey(fromDataType, toDataType));
        if (transformer == null) {
            throw new IllegalArgumentException("No transformer from: " + fromType + " to:" + toType);
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getMessage();

        try {
            DataType fromDT = fromDataType;
            Transformer target = transformer;
            if (fromType == null && message instanceof DataTypeAware dta && dta.hasDataType()) {
                fromDT = dta.getDataType();
                target = getCamelContext().getTransformerRegistry()
                        .resolveTransformer(new TransformerKey(fromDT, toDataType));
                if (target == null) {
                    exchange.setException(new IllegalArgumentException(
                            "Cannot resolve transformer from: " + fromDT + " to: " + toDataType));
                }
            }
            if (target != null) {
                target.transform(message, fromDT, toDataType);
            }
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(true);
        }

        return true;
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

}
