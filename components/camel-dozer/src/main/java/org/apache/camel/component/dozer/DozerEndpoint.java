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

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.converter.dozer.DozerTypeConverterLoader;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;
import org.dozer.CustomConverter;
import org.dozer.DozerBeanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "dozer", title = "Dozer", syntax = "dozer:name", producerOnly = true, label = "transformation")
public class DozerEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(DozerEndpoint.class);

    // IDs for built-in custom converters used with the Dozer component
    private static final String CUSTOM_MAPPING_ID = "_customMapping";
    private static final String VARIABLE_MAPPING_ID = "_variableMapping";
    private static final String EXPRESSION_MAPPING_ID = "_expressionMapping";

    private DozerBeanMapper mapper;
    private VariableMapper variableMapper;
    private CustomMapper customMapper;
    private ExpressionMapper expressionMapper;

    @UriParam
    private DozerConfiguration configuration;

    public DozerEndpoint(String endpointUri, Component component, DozerConfiguration configuration) throws Exception {
        super(endpointUri, component);
        this.configuration = configuration;
        variableMapper = new VariableMapper();
        customMapper = new CustomMapper(getCamelContext().getClassResolver());
        expressionMapper = new ExpressionMapper();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DozerProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for Dozer endpoints");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public DozerBeanMapper getMapper() throws Exception {
        return mapper;
    }

    public DozerConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(DozerConfiguration configuration) {
        this.configuration = configuration;
    }

    CustomMapper getCustomMapper() {
        return customMapper;
    }

    VariableMapper getVariableMapper() {
        return variableMapper;
    }

    ExpressionMapper getExpressionMapper() {
        return expressionMapper;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (mapper == null) {
            if (configuration.getMappingConfiguration() != null) {
                mapper = DozerTypeConverterLoader.createDozerBeanMapper(
                        configuration.getMappingConfiguration());
            } else {
                mapper = createDozerBeanMapper();
            }
            configureMapper(mapper);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // noop
    }

    private DozerBeanMapper createDozerBeanMapper() throws Exception {
        DozerBeanMapper answer = DozerComponent.createDozerBeanMapper(Collections.<String>emptyList());
        InputStream mapStream = null;
        try {
            LOG.info("Loading Dozer mapping file {}.", configuration.getMappingFile());
            // create the mapper instance and add the mapping file
            mapStream = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), configuration.getMappingFile());
            answer.addMapping(mapStream);
        } finally {
            IOHelper.close(mapStream);
        }
        return answer;
    }

    private void configureMapper(DozerBeanMapper mapper) throws Exception {
        // add our built-in converters
        Map<String, CustomConverter> converters = new HashMap<String, CustomConverter>();
        converters.put(CUSTOM_MAPPING_ID, customMapper);
        converters.put(VARIABLE_MAPPING_ID, variableMapper);
        converters.put(EXPRESSION_MAPPING_ID, expressionMapper);
        converters.putAll(mapper.getCustomConvertersWithId());
        mapper.setCustomConvertersWithId(converters);
    }
}
