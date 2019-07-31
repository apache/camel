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
package org.apache.camel.converter.dozer;

import com.github.dozermapper.core.DozerBeanMapperBuilder;
import com.github.dozermapper.core.Mapper;
import com.github.dozermapper.core.config.SettingsKeys;
import com.github.dozermapper.core.el.DefaultELEngine;
import com.github.dozermapper.core.el.ELEngine;
import com.github.dozermapper.core.el.ELExpressionFactory;
import com.github.dozermapper.core.el.NoopELEngine;
import com.github.dozermapper.core.el.TcclELEngine;
import com.github.dozermapper.core.util.RuntimeUtils;
import org.apache.camel.CamelContext;
import org.apache.camel.component.dozer.DozerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapperFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MapperFactory.class);

    private final CamelContext camelContext;
    private final DozerBeanMapperConfiguration configuration;

    public MapperFactory(CamelContext camelContext, DozerBeanMapperConfiguration configuration) {
        this.camelContext = camelContext;
        this.configuration = configuration;
    }

    public Mapper create() {
        if (camelContext == null) {
            throw new IllegalStateException("CamelContext is null");
        }

        return parseConfiguration(configuration);
    }

    private Mapper parseConfiguration(DozerBeanMapperConfiguration configuration) {
        configureSettings();

        Mapper mapper;
        if (configuration == null) {
            mapper = DozerBeanMapperBuilder.buildDefault();
        } else {
            mapper = DozerBeanMapperBuilder.create()
                    .withMappingFiles(configuration.getMappingFiles())
                    .withCustomConverters(configuration.getCustomConverters())
                    .withEventListeners(configuration.getEventListeners())
                    .withCustomConvertersWithIds(configuration.getCustomConvertersWithId())
                    .withMappingBuilders(configuration.getBeanMappingBuilders())
                    .withCustomFieldMapper(configuration.getCustomFieldMapper())
                    .withELEngine(createELEngine())
                    .build();
        }

        mapper.getMappingMetadata();

        return mapper;
    }

    private void configureSettings() {
        System.setProperty(SettingsKeys.CLASS_LOADER_BEAN, DozerThreadContextClassLoader.class.getName());
    }

    private ELEngine createELEngine() {
        ELEngine answer;

        ClassLoader appcl = camelContext.getApplicationContextClassLoader();
        ClassLoader auxcl = appcl == null ? DozerEndpoint.class.getClassLoader() : appcl;

        if (ELExpressionFactory.isSupported(auxcl)) {
            if (RuntimeUtils.isOSGi()) {
                answer = new TcclELEngine(ELExpressionFactory.newInstance(auxcl), auxcl);
            } else {
                answer = new DefaultELEngine(ELExpressionFactory.newInstance());
            }
        } else {
            LOG.warn("Expressions are not supported by Dozer. Are you missing javax.el dependency?");

            answer = new NoopELEngine();
        }

        return answer;
    }
}
