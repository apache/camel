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
package org.apache.camel.processor;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.Contract;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@code CamelInternalProcessorAdvice} which performs Transformation and Validation
 * according to the data type Contract.
 * 
 * TODO add declarative validation
 * @see CamelInternalProcessor, CamelInternalProcessorAdvice
 */
public class ContractAdvice implements CamelInternalProcessorAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(CamelInternalProcessor.class);

    private Contract contract;
    
    public ContractAdvice(Contract contract) {
        this.contract = contract;
    }
    
    @Override
    public Object before(Exchange exchange) throws Exception {
        DataType from = getCurrentType(exchange, Exchange.INPUT_TYPE);
        DataType to = contract.getInputType();
        if (to != null && !to.equals(from)) {
            LOG.debug("Looking for transformer for INPUT: from='{}', to='{}'", from, to);
            doTransform(exchange.getIn(), from, to);
            exchange.setProperty(Exchange.INPUT_TYPE, to);
        }
        return null;
    }
    
    @Override
    public void after(Exchange exchange, Object data) throws Exception {
        Message target = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        if (!exchange.hasOut() && exchange.getProperty(Exchange.OUTPUT_TYPE) == null) {
            exchange.setProperty(Exchange.OUTPUT_TYPE, exchange.getProperty(Exchange.INPUT_TYPE));
        }
        DataType from = getCurrentType(exchange, Exchange.OUTPUT_TYPE);
        DataType to = contract.getOutputType();
        if (to != null && !to.equals(from)) {
            LOG.debug("Looking for transformer for OUTPUT: from='{}', to='{}'", from, to);
            doTransform(target, from, to);
            exchange.setProperty(Exchange.OUTPUT_TYPE, to);
        }
    }
    
    private void doTransform(Message message, DataType from, DataType to) throws Exception {
        // transform into 'from' type before performing declared transformation
        convertIfRequired(message, from);
        
        if (applyExactlyMatchedTransformer(message, from, to)) {
            // Found exactly matched transformer. Java-Java transformer is also allowed.
            return;
        } else if (from == null || from.isJavaType()) {
            if (convertIfRequired(message, to)) {
                // Java->Java transformation just relies on TypeConverter if no explicit transformer
                return;
            } else if (from == null) {
                // use body class as a from type, or do nothing with assuming it's already in expected shape
                applyTransformerByClass(message, to);
                return;
            } else if (applyTransformerByToModel(message, from, to)) {
                // Java->Other transformation - found a transformer supports 'to' data model
                return;
            }
        } else if (from != null) {
            if (to.isJavaType()) {
                if (applyTransformerByFromModel(message, from, to)) {
                    // Other->Java transformation - found a transformer supprts 'from' data model
                    return;
                }
            } else if (applyTransformerChain(message, from, to)) {
                // Other->Other transformation - found a transformer chain
                return;
            }
        }
        
        throw new IllegalArgumentException("No Transformer found for [from='" + from + "', to='" + to + "']");
    }
    
    private boolean convertIfRequired(Message message, DataType type) throws Exception {
        // TODO for better performance it may be better to add TypeConveterTransformer
        // into transformer registry automatically to avoid unnecessary scan in transformer registry
        if (type != null && type.isJavaType() && type.getName() != null) {
            CamelContext context = message.getExchange().getContext();
            Class<?> typeJava = getClazz(type.getName(), context);
            if (!typeJava.isAssignableFrom(message.getBody().getClass())) {
                LOG.debug("Converting to '{}'", typeJava.getName());
                message.setBody(message.getMandatoryBody(typeJava));
                return true;
            }
        }
        return false;
    }
    
    private boolean applyTransformer(Transformer transformer, Message message, DataType from, DataType to) throws Exception {
        if (transformer != null) {
            LOG.debug("Applying transformer: from='{}', to='{}', transformer='{}'", from, to, transformer);
            transformer.transform(message, from, to);
            return true;
        }
        return false;
    }
    private boolean applyExactlyMatchedTransformer(Message message, DataType from, DataType to) throws Exception {
        Transformer transformer = message.getExchange().getContext().resolveTransformer(from, to);
        return applyTransformer(transformer, message, from, to);
    }
    
    private boolean applyTransformerByClass(Message message, DataType to) throws Exception {
        DataType from = new DataType(message.getBody().getClass());
        Transformer transformer = message.getExchange().getContext().resolveTransformer(from, to);
        return applyTransformer(transformer, message, from, to);
    }
    
    private boolean applyTransformerByToModel(Message message, DataType from, DataType to) throws Exception {
        Transformer transformer = message.getExchange().getContext().resolveTransformer(to.getModel());
        return applyTransformer(transformer, message, from, to);
    }
    
    private boolean applyTransformerByFromModel(Message message, DataType from, DataType to) throws Exception {
        Transformer transformer = message.getExchange().getContext().resolveTransformer(from.getModel());
        return applyTransformer(transformer, message, from, to);
    }
    
    private boolean applyTransformerChain(Message message, DataType from, DataType to) throws Exception {
        CamelContext context = message.getExchange().getContext();
        Transformer fromTransformer = context.resolveTransformer(from.getModel());
        Transformer toTransformer = context.resolveTransformer(to.getModel());
        if (fromTransformer != null && toTransformer != null) {
            LOG.debug("Applying transformer 1/2: from='{}', to='{}', transformer='{}'", from, to, fromTransformer);
            fromTransformer.transform(message, from, new DataType(Object.class));
            LOG.debug("Applying transformer 2/2: from='{}', to='{}', transformer='{}'", from, to, toTransformer);
            toTransformer.transform(message, new DataType(Object.class), to);
            return true;
        }
        return false;
    }
    
    private Class<?> getClazz(String type, CamelContext context) throws Exception {
        return context.getClassResolver().resolveMandatoryClass(type);
    }
    
    private DataType getCurrentType(Exchange exchange, String name) {
        Object prop = exchange.getProperty(name);
        if (prop instanceof DataType) {
            return (DataType)prop;
        } else if (prop instanceof String) {
            DataType answer = new DataType((String)prop);
            exchange.setProperty(name, answer);
            return answer;
        }
        return null;
    }
}