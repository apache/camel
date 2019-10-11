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
package org.apache.camel.dataformat.bindy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.dataformat.bindy.annotation.FormatFactories;
import org.apache.camel.dataformat.bindy.annotation.Link;
import org.apache.camel.dataformat.bindy.format.factories.DefaultFactoryRegistry;
import org.apache.camel.dataformat.bindy.format.factories.FactoryRegistry;
import org.apache.camel.dataformat.bindy.format.factories.FormatFactoryInterface;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BindyAbstractDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(BindyAbstractDataFormat.class);
    private String locale;
    private BindyAbstractFactory modelFactory;
    private Class<?> classType;
    private CamelContext camelContext;
    private boolean unwrapSingleInstance = true;
    private boolean allowEmptyStream;

    public BindyAbstractDataFormat() {
    }

    protected BindyAbstractDataFormat(Class<?> classType) {
        this.classType = classType;
    }

    public Class<?> getClassType() {
        return classType;
    }

    public void setClassType(Class<?> classType) {
        this.classType = classType;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public boolean isUnwrapSingleInstance() {
        return unwrapSingleInstance;
    }

    public void setUnwrapSingleInstance(boolean unwrapSingleInstance) {
        this.unwrapSingleInstance = unwrapSingleInstance;
    }

    public boolean isAllowEmptyStream() {
        return allowEmptyStream;
    }

    public void setAllowEmptyStream(boolean allowEmptyStream) {
        this.allowEmptyStream = allowEmptyStream;
    }

    public BindyAbstractFactory getFactory() throws Exception {
        if (modelFactory == null) {
            FormatFactory formatFactory = createFormatFactory();
            registerAdditionalConverter(formatFactory);
            modelFactory = createModelFactory(formatFactory);
            modelFactory.setLocale(locale);
        }
        return modelFactory;
    }

    private void registerAdditionalConverter(FormatFactory formatFactory) throws IllegalAccessException, InstantiationException {
        Function<Class<?>, FormatFactories> g = aClass -> aClass.getAnnotation(FormatFactories.class);
        Function<FormatFactories, List<Class<? extends FormatFactoryInterface>>> h = formatFactories -> Arrays.asList(formatFactories.value());
        List<Class<? extends FormatFactoryInterface>> array = Optional
                .ofNullable(classType)
                .map(g)
                .map(h)
                .orElse(Collections.emptyList());
        for (Class<? extends FormatFactoryInterface> l : array) {
            formatFactory.getFactoryRegistry().register(l.newInstance());
        }
    }

    private FormatFactory createFormatFactory() {
        FormatFactory formatFactory = new FormatFactory();
        FactoryRegistry factoryRegistry = createFactoryRegistry();
        formatFactory.setFactoryRegistry(factoryRegistry);
        return formatFactory;
    }

    private FactoryRegistry createFactoryRegistry() {
        return tryToGetFactoryRegistry();
    }

    private FactoryRegistry tryToGetFactoryRegistry() {
        Function<CamelContext, Registry> f = CamelContext::getRegistry;
        Function<Registry, Set<FactoryRegistry>> g = r -> r.findByType(FactoryRegistry.class);
        Function<Set<FactoryRegistry>, FactoryRegistry> h = factoryRegistries -> {
            if (factoryRegistries.size() > 1) {
                LOGGER.warn("Number of registered {}: {}",
                        FactoryRegistry.class.getCanonicalName(),
                        factoryRegistries.size());
            }
            if (factoryRegistries.iterator().hasNext()) {
                return factoryRegistries.iterator().next();
            } else {
                return new DefaultFactoryRegistry();
            }
        };

        return Optional.ofNullable(camelContext)
                .map(f)
                .map(g)
                .map(h)
                .orElse(new DefaultFactoryRegistry());
    }

    public void setModelFactory(BindyAbstractFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    protected Map<String, Object> createLinkedFieldsModel(Object model) throws IllegalAccessException {
        Map<String, Object> row = new HashMap<>();
        createLinkedFieldsModel(model, row);
        return row;
    }

    protected void createLinkedFieldsModel(Object model, Map<String, Object> row) throws IllegalAccessException {
        for (Field field : model.getClass().getDeclaredFields()) {
            Link linkField = field.getAnnotation(Link.class);
            if (linkField != null) {
                boolean accessible = field.isAccessible();
                field.setAccessible(true);
                if (!row.containsKey(field.getType().getName())) {
                    row.put(field.getType().getName(), field.get(model));
                }
                field.setAccessible(accessible);
            }
        }
    }

    protected abstract BindyAbstractFactory createModelFactory(FormatFactory formatFactory) throws Exception;

    protected Object extractUnmarshalResult(List<Map<String, Object>> models) {
        if (getClassType() != null) {
            // we expect to findForFormattingOptions this type in the models, and grab only that type
            List<Object> answer = new ArrayList<>();
            for (Map<String, Object> entry : models) {
                Object data = entry.get(getClassType().getName());
                if (data != null) {
                    answer.add(data);
                }
            }
            // if there is only 1 then dont return a list
            if (isUnwrapSingleInstance() && answer.size() == 1) {
                return answer.get(0);
            } else {
                return answer;
            }
        } else {
            return models;
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
