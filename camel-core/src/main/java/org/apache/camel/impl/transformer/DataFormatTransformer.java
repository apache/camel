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
package org.apache.camel.impl.transformer;

import java.io.InputStream;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.converter.stream.OutputStreamBuilder;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Transformer} implementation which leverages {@link DataFormat} to perform transformation.
 * 
 * {@see Transformer}
 */
public class DataFormatTransformer extends Transformer {
    private static final Logger LOG = LoggerFactory.getLogger(DataFormatTransformer.class);

    private String dataFormatRef;
    private DataFormatDefinition dataFormatType;
    private DataFormat dataFormat;
    private String transformerString;

    public DataFormatTransformer(CamelContext context) {
        setCamelContext(context);
    }

    /**
     * Perform data transformation with specified from/to type using DataFormat.
     * @param message message to apply transformation
     * @param from 'from' data type
     * @param to 'to' data type
     */
    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        Exchange exchange = message.getExchange();
        CamelContext context = exchange.getContext();
        
        // Unmarshaling into Java Object
        if ((to == null || to.isJavaType()) && (from.equals(getFrom()) || from.getModel().equals(getModel()))) {
            DataFormat dataFormat = getDataFormat(exchange);
            LOG.debug("Unmarshaling with '{}'", dataFormat);
            Object answer = dataFormat.unmarshal(exchange, message.getBody(InputStream.class));
            if (to != null && to.getName() != null) {
                Class<?> toClass = context.getClassResolver().resolveClass(to.getName());
                if (!toClass.isAssignableFrom(answer.getClass())) {
                    LOG.debug("Converting to '{}'", toClass.getName());
                    answer = context.getTypeConverter().mandatoryConvertTo(toClass, answer);
                }
            }
            message.setBody(answer);
            
        // Marshaling from Java Object
        } else if ((from == null || from.isJavaType()) && (to.equals(getTo()) || to.getModel().equals(getModel()))) {
            Object input = message.getBody();
            if (from != null && from.getName() != null) {
                Class<?> fromClass = context.getClassResolver().resolveClass(from.getName());
                if (!fromClass.isAssignableFrom(input.getClass())) {
                    LOG.debug("Converting to '{}'", fromClass.getName());
                    input = context.getTypeConverter().mandatoryConvertTo(fromClass, input);
                }
            }
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);
            DataFormat dataFormat = getDataFormat(exchange);
            LOG.debug("Marshaling with '{}'", dataFormat);
            dataFormat.marshal(exchange, message.getBody(), osb);
            message.setBody(osb.build());
            
        } else {
            throw new IllegalArgumentException("Unsupported transformation: from='" + from + ", to='" + to + "'");
        }
    }

    /**
     * A bit dirty hack to create DataFormat instance, as it requires a RouteContext anyway.
     */
    private DataFormat getDataFormat(Exchange exchange) throws Exception {
        if (this.dataFormat == null) {
            this.dataFormat = DataFormatDefinition.getDataFormat(
                exchange.getUnitOfWork().getRouteContext(), this.dataFormatType, this.dataFormatRef);
            if (this.dataFormat != null && !getCamelContext().hasService(this.dataFormat)) {
                getCamelContext().addService(this.dataFormat, false);
            }
        }
        return this.dataFormat;
    }

    /**
     * Set DataFormat ref.
     * @param ref DataFormat ref
     * @return this DataFormatTransformer instance
     */
    public DataFormatTransformer setDataFormatRef(String ref) {
        this.dataFormatRef = ref;
        this.transformerString = null;
        return this;
    }

    /**
     * Set DataFormatDefinition.
     * @param dataFormatType DataFormatDefinition
     * @return this DataFormatTransformer instance
     */
    public DataFormatTransformer setDataFormatType(DataFormatDefinition dataFormatType) {
        this.dataFormatType = dataFormatType;
        this.transformerString = null;
        return this;
    }

    @Override
    public String toString() {
        if (transformerString == null) {
            transformerString =
                String.format("DataFormatTransformer[scheme='%s', from='%s', to='%s', ref='%s', type='%s']",
                    getModel(), getFrom(), getTo(), dataFormatRef, dataFormatType);
        }
        return transformerString;
    }

    @Override
    public void doStart() throws Exception {
        // no-op
    }

    @Override
    public void doStop() throws Exception {
        ServiceHelper.stopService(this.dataFormat);
        getCamelContext().removeService(this.dataFormat);
    }
}
