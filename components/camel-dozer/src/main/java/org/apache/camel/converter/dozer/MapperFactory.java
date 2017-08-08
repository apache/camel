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
package org.apache.camel.converter.dozer;

import java.lang.reflect.Field;
import java.util.Map;

import javax.el.ExpressionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.component.dozer.DozerEndpoint;
import org.apache.camel.util.ReflectionHelper;
import org.dozer.CustomConverter;
import org.dozer.DozerBeanMapperBuilder;
import org.dozer.DozerEventListener;
import org.dozer.Mapper;
import org.dozer.config.BeanContainer;
import org.dozer.config.GlobalSettings;
import org.dozer.loader.api.BeanMappingBuilder;
import org.dozer.loader.xml.ELEngine;
import org.dozer.loader.xml.ExpressionElementReader;
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
        DozerBeanMapperBuilder builder = DozerBeanMapperBuilder.create();
        if (configuration != null) {
            if (configuration.getMappingFiles() != null) {
                String[] files = configuration.getMappingFiles().toArray(new String[configuration.getMappingFiles().size()]);
                builder.withMappingFiles(files);
            }

            if (configuration.getCustomConverters() != null) {
                for (CustomConverter current : configuration.getCustomConverters()) {
                    builder.withCustomConverter(current);
                }
            }
            if (configuration.getEventListeners() != null) {
                for (DozerEventListener current : configuration.getEventListeners()) {
                    builder.withEventListener(current);
                }
            }

            if (configuration.getCustomConvertersWithId() != null) {
                for (Map.Entry<String, CustomConverter> current : configuration.getCustomConvertersWithId().entrySet()) {
                    builder.withCustomConverterWithId(current.getKey(), current.getValue());
                }
            }

            if (configuration.getBeanMappingBuilders() != null) {
                for (BeanMappingBuilder current : configuration.getBeanMappingBuilders()) {
                    builder.withMappingBuilder(current);
                }
            }

            if (configuration.getCustomFieldMapper() != null) {
                builder.withCustomFieldMapper(configuration.getCustomFieldMapper());
            }
        }

        Mapper mapper = builder.build();

        configureGlobalSettings(mapper);
        configureBeanContainer(mapper, configuration);

        mapper.getMappingMetadata();

        return mapper;
    }

    /**
     * Sets hidden fields on the mapper and returns an instance
     * NOTE: https://github.com/DozerMapper/dozer/issues/463
     *
     * @param mapper
     */
    private void configureGlobalSettings(Mapper mapper) {
        GlobalSettings settings;
        try {
            LOG.info("Attempting to retrieve GlobalSettings from: " + mapper);
            Field field = mapper.getClass().getDeclaredField("globalSettings");
            field.setAccessible(true);

            settings = (GlobalSettings)field.get(mapper);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot retrieve Dozer GlobalSettings due " + e.getMessage(), e);
        }

        //Safety check
        if (settings == null) {
            throw new IllegalStateException("Cannot retrieve Dozer GlobalSettings due null reflection response");
        }

        try {
            LOG.info("Configuring GlobalSettings to use Camel classloader: {}", DozerThreadContextClassLoader.class.getName());
            Field field = settings.getClass().getDeclaredField("classLoaderBeanName");
            ReflectionHelper.setField(field, settings, DozerThreadContextClassLoader.class.getName());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot configure Dozer GlobalSettings to use DozerThreadContextClassLoader as classloader due " + e.getMessage(), e);
        }

        try {
            LOG.info("Configuring GlobalSettings to enable EL");
            Field field = settings.getClass().getDeclaredField("elEnabled");
            ReflectionHelper.setField(field, settings, true);
        } catch (NoSuchFieldException nsfEx) {
            throw new IllegalStateException("Failed to enable EL in global Dozer settings", nsfEx);
        }
    }

    public void configureBeanContainer(Mapper mapper, DozerBeanMapperConfiguration configuration) {
        String elprop = System.getProperty("javax.el.ExpressionFactory");
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader appcl = camelContext.getApplicationContextClassLoader();
            ClassLoader auxcl = appcl != null ? appcl : DozerEndpoint.class.getClassLoader();
            Thread.currentThread().setContextClassLoader(auxcl);
            try {
                Class<?> clazz = auxcl.loadClass("com.sun.el.ExpressionFactoryImpl");
                ExpressionFactory factory = (ExpressionFactory)clazz.newInstance();
                System.setProperty("javax.el.ExpressionFactory", factory.getClass().getName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                LOG.debug("Cannot load glasfish expression engine, using default");
            }

            BeanContainer beanContainer = resolveBeanContainer(mapper);
            if (beanContainer.getElEngine() == null) {
                ELEngine engine = new ELEngine();
                engine.init();

                beanContainer.setElEngine(engine);
            }

            beanContainer.setElementReader(new ExpressionElementReader(beanContainer.getElEngine()));
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
            if (elprop == null) {
                System.clearProperty("javax.el.ExpressionFactory");
            } else {
                System.setProperty("javax.el.ExpressionFactory", elprop);
            }
        }
    }

    private BeanContainer resolveBeanContainer(Mapper mapper) {
        LOG.info("Attempting to retrieve BeanContainer from: " + mapper);

        BeanContainer beanContainer = (BeanContainer)resolveProperty(mapper, "beanContainer");
        if (beanContainer == null) {
            throw new IllegalStateException("Cannot retrieve Dozer BeanContainer due null response");
        }

        return beanContainer;
    }

    private static Object resolveProperty(Mapper mapper, String fieldName) {
        Object prop;
        try {
            Field field = mapper.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);

            prop = field.get(mapper);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot retrieve DozerBeanMapper." + fieldName + " due " + e.getMessage(), e);
        }

        return prop;
    }
}
