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

import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Transformer} implementation which leverages {@link DataFormat} to perform transformation.
 *
 * {@see Transformer}
 */
public class DataFormatTransformer extends Transformer {

    private static final Logger LOG = LoggerFactory.getLogger(DataFormatTransformer.class);

    private DataFormat dataFormat;
    private String transformerString;

    public DataFormatTransformer() {
    }

    public DataFormatTransformer(CamelContext context) {
        setCamelContext(context);
    }

    /**
     * Perform data transformation with specified from/to type using DataFormat.
     *
     * @param message message to apply transformation
     * @param from    'from' data type
     * @param to      'to' data type
     */
    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        Exchange exchange = message.getExchange();
        CamelContext context = exchange.getContext();

        // Unmarshaling into Java Object
        if ((DataType.isAnyType(to) || to.isJavaType()) && (from.equals(getFrom()) || from.getScheme().equals(getName()))) {
            LOG.debug("Unmarshaling with: {}", dataFormat);
            Object answer = dataFormat.unmarshal(exchange, message.getBody(InputStream.class));
            if (!DataType.isAnyType(to) && to.getName() != null) {
                Class<?> toClass = context.getClassResolver().resolveClass(to.getName());
                if (!toClass.isAssignableFrom(answer.getClass())) {
                    LOG.debug("Converting to: {}", toClass.getName());
                    answer = context.getTypeConverter().mandatoryConvertTo(toClass, answer);
                }
            }
            message.setBody(answer);

            // Marshaling from Java Object
        } else if ((DataType.isAnyType(from) || from.isJavaType())
                && (to.equals(getTo()) || to.getScheme().equals(getName()))) {
            Object input = message.getBody();
            if (!DataType.isAnyType(from) && from.getName() != null) {
                Class<?> fromClass = context.getClassResolver().resolveClass(from.getName());
                if (!fromClass.isAssignableFrom(input.getClass())) {
                    LOG.debug("Converting to: {}", fromClass.getName());
                    input = context.getTypeConverter().mandatoryConvertTo(fromClass, input);
                }
            }
            OutputStreamBuilder osb = OutputStreamBuilder.withExchange(exchange);
            LOG.debug("Marshaling with: {}", dataFormat);
            dataFormat.marshal(exchange, input, osb);
            message.setBody(osb.build());

        } else {
            throw new IllegalArgumentException("Unsupported transformation: from='" + from + ", to='" + to + "'");
        }
    }

    /**
     * Set DataFormat.
     *
     * @return this DataFormatTransformer instance
     */
    public DataFormatTransformer setDataFormat(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
        this.transformerString = null;
        return this;
    }

    @Override
    public String toString() {
        if (transformerString == null) {
            transformerString = String.format("DataFormatTransformer[name='%s', from='%s', to='%s', dataFormat='%s']",
                    getName(), getFrom(), getTo(), dataFormat);
        }
        return transformerString;
    }

    @Override
    public void doStart() throws Exception {
        ObjectHelper.notNull(dataFormat, "dataFormat");
        getCamelContext().addService(dataFormat, false);
    }

    @Override
    public void doStop() throws Exception {
        ServiceHelper.stopService(dataFormat);
        getCamelContext().removeService(dataFormat);
    }
}
