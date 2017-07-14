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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Transformer} implementation which leverages {@link Processor} to perform transformation.
 * 
 * {@see Transformer}
 */
public class ProcessorTransformer extends Transformer {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessorTransformer.class);

    private Processor processor;
    private String transformerString;

    public ProcessorTransformer(CamelContext context) {
        setCamelContext(context);
    }

    /**
     * Perform data transformation with specified from/to type using Processor.
     * @param message message to apply transformation
     * @param from 'from' data type
     * @param to 'to' data type
     */
    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        Exchange exchange = message.getExchange();
        CamelContext context = exchange.getContext();
        if (from.isJavaType()) {
            Object input = message.getBody();
            Class<?> fromClass = context.getClassResolver().resolveClass(from.getName());
            if (!fromClass.isAssignableFrom(input.getClass())) {
                LOG.debug("Converting to '{}'", fromClass.getName());
                input = context.getTypeConverter().mandatoryConvertTo(fromClass, input);
                message.setBody(input);
            }
        }
        
        LOG.debug("Sending to transform processor '{}'", processor);
        DefaultExchange transformExchange = new DefaultExchange(exchange);
        transformExchange.setIn(message);
        transformExchange.setProperties(exchange.getProperties());
        processor.process(transformExchange);
        Message answer = transformExchange.hasOut() ? transformExchange.getOut() : transformExchange.getIn();
        
        if (to.isJavaType()) {
            Object answerBody = answer.getBody();
            Class<?> toClass = context.getClassResolver().resolveClass(to.getName());
            if (!toClass.isAssignableFrom(answerBody.getClass())) {
                LOG.debug("Converting to '{}'", toClass.getName());
                answerBody = context.getTypeConverter().mandatoryConvertTo(toClass, answerBody);
                answer.setBody(answerBody);
            }
        }
        
        message.copyFrom(answer);
    }

    /**
     * Set processor to use
     *
     * @param processor Processor
     * @return this ProcessorTransformer instance
     */
    public ProcessorTransformer setProcessor(Processor processor) {
        this.processor = processor;
        this.transformerString = null;
        return this;
    }

    @Override
    public String toString() {
        if (transformerString == null) {
            transformerString =
                String.format("ProcessorTransformer[scheme='%s', from='%s', to='%s', processor='%s']",
                    getModel(), getFrom(), getTo(), processor);
        }
        return transformerString;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(processor, "processor", this);
        ServiceHelper.startService(this.processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(this.processor);
    }
}
