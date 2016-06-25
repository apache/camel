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
package org.apache.camel.component.dozer;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.processor.MarshalProcessor;
import org.apache.camel.processor.UnmarshalProcessor;
import org.apache.camel.spi.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer class for Dozer endpoints.
 */
public class DozerProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DozerProducer.class);
    
    private DozerEndpoint endpoint;
    private UnmarshalProcessor unmarshaller;
    private MarshalProcessor marshaller;

    /**
     * Create a new producer for dozer endpoints.
     * @param endpoint endpoint requiring a producer
     */
    public DozerProducer(DozerEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Unmarshal the source content only if an unmarshaller is configured.
        String unmarshalId = endpoint.getConfiguration().getUnmarshalId();
        if (unmarshalId != null) {
            LOG.debug("Unmarshalling input data using data format '{}'.", unmarshalId);
            resolveUnmarshaller(exchange, unmarshalId).process(exchange);
            if (exchange.getException() != null) {
                throw exchange.getException();
            }
        }
        
        // Load the target model class
        Class<?> targetModel = endpoint.getCamelContext().getClassResolver().resolveMandatoryClass(
                endpoint.getConfiguration().getTargetModel());
        
        // If an unmarshaller was used, the unmarshalled message is the OUT message.
        Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        
        // Convert to source model, if specified
        String sourceType = endpoint.getConfiguration().getSourceModel();
        if (sourceType != null) {
            LOG.debug("Converting to source model {}.", sourceType);
            Class<?> sourceModel = endpoint.getCamelContext()
                    .getClassResolver().resolveClass(sourceType);
            if (sourceModel == null) {
                throw new Exception("Unable to load sourceModel class: " + sourceType);
            }
            msg.setBody(msg.getBody(sourceModel));
        }
        
        // Perform mappings
        LOG.debug("Mapping to target model {}.", targetModel.getName());
        Object targetObject = endpoint.getMapper().map(msg.getBody(), targetModel);
        // Second pass to process literal mappings
        endpoint.getMapper().map(endpoint.getVariableMapper(), targetObject);
        // Third pass to process expression mappings
        try {
            endpoint.getExpressionMapper().setCurrentExchange(exchange);
            endpoint.getMapper().map(endpoint.getExpressionMapper(), targetObject);
        } finally {
            // Clear out the exchange reference on the expression mapper
            endpoint.getExpressionMapper().setCurrentExchange(null);
        }
        msg.setBody(targetObject);
        exchange.setIn(msg);
        
        // Marshal the source content only if a marshaller is configured.
        String marshalId = endpoint.getConfiguration().getMarshalId();
        if (marshalId != null) {
            LOG.debug("Marshalling output data using data format '{}'.", marshalId);
            resolveMarshaller(exchange, marshalId).process(exchange);
            if (exchange.getException() != null) {
                throw exchange.getException();
            }
        }
    }
    
    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (unmarshaller != null) {
            unmarshaller.stop();
        }
        if (marshaller != null) {
            marshaller.stop();
        }
    }
    
    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        if (unmarshaller != null) {
            unmarshaller.shutdown();
        }
        if (marshaller != null) {
            marshaller.shutdown();
        }
    }
    
    /**
     * Find and configure an unmarshaller for the specified data format.
     */
    private synchronized UnmarshalProcessor resolveUnmarshaller(
            Exchange exchange, String dataFormatId) throws Exception {
        
        if (unmarshaller == null) {
            DataFormat dataFormat = DataFormatDefinition.getDataFormat(
                    exchange.getUnitOfWork().getRouteContext(), null, dataFormatId);
            if (dataFormat == null) {
                throw new Exception("Unable to resolve data format for unmarshalling: " + dataFormatId);
            }
            
            // Wrap the data format in a processor and start/configure it.  
            // Stop/shutdown is handled when the corresponding methods are 
            // called on this producer.
            unmarshaller = new UnmarshalProcessor(dataFormat);
            unmarshaller.setCamelContext(exchange.getContext());
            unmarshaller.start();
        }
        return unmarshaller;
    }
    
    /**
     * Find and configure an unmarshaller for the specified data format.
     */
    private synchronized MarshalProcessor resolveMarshaller(
            Exchange exchange, String dataFormatId) throws Exception {
        
        if (marshaller == null) {
            DataFormat dataFormat = DataFormatDefinition.getDataFormat(
                    exchange.getUnitOfWork().getRouteContext(), null, dataFormatId);
            if (dataFormat == null) {
                throw new Exception("Unable to resolve data format for marshalling: " + dataFormatId);
            }
            
            // Wrap the data format in a processor and start/configure it.  
            // Stop/shutdown is handled when the corresponding methods are 
            // called on this producer.
            marshaller = new MarshalProcessor(dataFormat);
            marshaller.setCamelContext(exchange.getContext());
            marshaller.start();
        }
        return marshaller;
    }
    
}
