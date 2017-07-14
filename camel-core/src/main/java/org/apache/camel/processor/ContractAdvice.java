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
import org.apache.camel.ValidationException;
import org.apache.camel.model.InputTypeDefinition;
import org.apache.camel.model.OutputTypeDefinition;
import org.apache.camel.spi.Contract;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.apache.camel.spi.Transformer;
import org.apache.camel.spi.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CamelInternalProcessorAdvice} which applies {@link Transformer} and {@link Validator}
 * according to the data type Contract.
 * <p/>
 * The default camel {@link Message} implements {@link DataTypeAware} which
 * holds a {@link DataType} to indicate current message type. If the input type
 * declared by {@link InputTypeDefinition} is different from current IN message type,
 * camel internal processor look for a Transformer which transforms from the current
 * message type to the expected message type before routing.
 * After routing, if the output type declared by {@link OutputTypeDefinition} is different
 * from current OUT message (or IN message if no OUT), camel look for a Transformer and apply.
 * 
 * @see Transformer
 * @see Validator
 * @see InputTypeDefinition
 * @see OutputTypeDefinition
 */
public class ContractAdvice implements CamelInternalProcessorAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(ContractAdvice.class);

    private Contract contract;
    
    public ContractAdvice(Contract contract) {
        this.contract = contract;
    }
    
    @Override
    public Object before(Exchange exchange) throws Exception {
        if (!(exchange.getIn() instanceof DataTypeAware)) {
            return null;
        }
        DataType to = contract.getInputType();
        if (to != null) {
            DataTypeAware target = (DataTypeAware)exchange.getIn();
            DataType from = target.getDataType();
            if (!to.equals(from)) {
                LOG.debug("Looking for transformer for INPUT: from='{}', to='{}'", from, to);
                doTransform(exchange.getIn(), from, to);
                target.setDataType(to);
            }
            if (contract.isValidateInput()) {
                doValidate(exchange.getIn(), to);
            }
        }
        return null;
    }
    
    @Override
    public void after(Exchange exchange, Object data) throws Exception {
        if (exchange.isFailed()) {
            // TODO can we add FAULT_TYPE processing?
            return;
        }

        Message target = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        if (!(target instanceof DataTypeAware)) {
            return;
        }
        DataType to = contract.getOutputType();
        if (to != null) {
            DataTypeAware typeAwareTarget = (DataTypeAware)target;
            DataType from = typeAwareTarget.getDataType();
            if (!to.equals(from)) {
                LOG.debug("Looking for transformer for OUTPUT: from='{}', to='{}'", from, to);
                doTransform(target, from, to);
                typeAwareTarget.setDataType(to);
            }
            if (contract.isValidateOutput()) {
                doValidate(target, to);
            }
        }
    }
    
    private void doTransform(Message message, DataType from, DataType to) throws Exception {
        if (from == null) {
            // If 'from' is null, only Java-Java convertion is performed.
            // It means if 'to' is other than Java, it's assumed to be already in expected type.
            convertIfRequired(message, to);
            return;
        }
        
        // transform into 'from' type before performing declared transformation
        convertIfRequired(message, from);
        
        if (applyMatchedTransformer(message, from, to)) {
            // Found matched transformer. Java-Java transformer is also allowed.
            return;
        } else if (from.isJavaType()) {
            // Try TypeConverter as a fallback for Java->Java transformation
            convertIfRequired(message, to);
            // If Java->Other transformation required but no transformer matched,
            // then assume it's already in expected type, i.e. do nothing.
            return;
        } else if (applyTransformerChain(message, from, to)) {
            // Other->Other transformation - found a transformer chain
            return;
        }
        
        throw new IllegalArgumentException("No Transformer found for [from='" + from + "', to='" + to + "']");
    }
    
    private boolean convertIfRequired(Message message, DataType type) throws Exception {
        // TODO for better performance it may be better to add TypeConverterTransformer
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
    private boolean applyMatchedTransformer(Message message, DataType from, DataType to) throws Exception {
        Transformer transformer = message.getExchange().getContext().resolveTransformer(from, to);
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

    private void doValidate(Message message, DataType type) throws ValidationException {
        Validator validator = message.getExchange().getContext().resolveValidator(type);
        if (validator != null) {
            LOG.debug("Applying validator: type='{}', validator='{}'", type, validator);
            validator.validate(message, type);
        } else {
            throw new ValidationException(message.getExchange(), String.format("No Validator found for '%s'", type));
        }
    }
}