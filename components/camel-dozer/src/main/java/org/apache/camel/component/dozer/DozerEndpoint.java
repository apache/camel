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
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;
import org.dozer.CustomConverter;
import org.dozer.DozerBeanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "dozer", producerOnly = true, label = "transformation")
public class DozerEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(DozerEndpoint.class);

    // IDs for built-in custom converters used with the Dozer component
    private static final String CUSTOM_MAPPING_ID = "_customMapping";
    private static final String LITERAL_MAPPING_ID = "_literalMapping";

    private DozerBeanMapper mapper;
    private LiteralMapper literalMapper;
    private CustomMapper customMapper;

    @UriParam
    private DozerConfiguration configuration;

    public DozerEndpoint(String endpointUri, Component component, DozerConfiguration configuration) throws Exception {
        super(endpointUri, component);
        this.configuration = configuration;
        literalMapper = new LiteralMapper();
        customMapper = new CustomMapper(getCamelContext().getClassResolver());
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
    
    LiteralMapper getLiteralMapper() {
        return literalMapper;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (mapper == null) {
            mapper = createDozerBeanMapper();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // noop
    }

    private DozerBeanMapper createDozerBeanMapper() throws Exception {
        DozerBeanMapper answer = new DozerBeanMapper();
        InputStream mapStream = null;
        try {
            LOG.info("Loading Dozer mapping file {}.", configuration.getMappingFile());
            // create the mapper instance and add the mapping file
            mapStream = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext().getClassResolver(), configuration.getMappingFile());
            answer.addMapping(mapStream);
            
            // add our built-in converters
            Map<String, CustomConverter> converters = new HashMap<String, CustomConverter>();
            converters.put(CUSTOM_MAPPING_ID, customMapper);
            converters.put(LITERAL_MAPPING_ID, literalMapper);
            converters.putAll(answer.getCustomConvertersWithId());
            answer.setCustomConvertersWithId(converters);
            
        } finally {
            IOHelper.close(mapStream);
        }

        return answer;
    }
}
