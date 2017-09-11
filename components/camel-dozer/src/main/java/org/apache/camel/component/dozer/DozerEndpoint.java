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

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.el.ExpressionFactory;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.converter.dozer.DozerBeanMapperConfiguration;
import org.apache.camel.converter.dozer.MapperFactory;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ResourceHelper;
import org.dozer.CustomConverter;
import org.dozer.Mapper;
import org.dozer.config.BeanContainer;
import org.dozer.loader.xml.ELEngine;
import org.dozer.loader.xml.ExpressionElementReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The dozer component provides the ability to map between Java beans using the Dozer mapping library.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "dozer", title = "Dozer", syntax = "dozer:name", producerOnly = true, label = "transformation")
public class DozerEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(DozerEndpoint.class);

    // IDs for built-in custom converters used with the Dozer component
    private static final String CUSTOM_MAPPING_ID = "_customMapping";
    private static final String VARIABLE_MAPPING_ID = "_variableMapping";
    private static final String EXPRESSION_MAPPING_ID = "_expressionMapping";

    private Mapper mapper;
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

    public Mapper getMapper() throws Exception {
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

        initDozerBeanContainerAndMapper();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // noop
    }

    protected void initDozerBeanContainerAndMapper() throws Exception {
        LOG.info("Configuring {}...", Mapper.class.getName());

        if (mapper == null) {
            if (configuration.getMappingConfiguration() == null) {
                URL url = ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), configuration.getMappingFile());

                DozerBeanMapperConfiguration config = new DozerBeanMapperConfiguration();
                config.setCustomConvertersWithId(getCustomConvertersWithId());
                config.setMappingFiles(Arrays.asList(url.toString()));

                configuration.setMappingConfiguration(config);
            } else {
                DozerBeanMapperConfiguration config = configuration.getMappingConfiguration();
                if (config.getCustomConvertersWithId() == null) {
                    config.setCustomConvertersWithId(getCustomConvertersWithId());
                } else {
                    config.getCustomConvertersWithId().putAll(getCustomConvertersWithId());
                }

                if (config.getMappingFiles() == null || config.getMappingFiles().size() <= 0) {
                    URL url = ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), configuration.getMappingFile());
                    config.setMappingFiles(Arrays.asList(url.toString()));
                }
            }

            MapperFactory factory = new MapperFactory(getCamelContext(), configuration.getMappingConfiguration());
            mapper = factory.create();
        }
    }

    private Map<String, CustomConverter> getCustomConvertersWithId() {
        Map<String, CustomConverter> customConvertersWithId = new HashMap<String, CustomConverter>();
        customConvertersWithId.put(CUSTOM_MAPPING_ID, customMapper);
        customConvertersWithId.put(VARIABLE_MAPPING_ID, variableMapper);
        customConvertersWithId.put(EXPRESSION_MAPPING_ID, expressionMapper);

        return customConvertersWithId;
    }
}
