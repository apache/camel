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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.dozer.Mapper;
import org.dozer.metadata.ClassMappingMetadata;
import org.dozer.metadata.MappingMetadata;
import org.dozer.util.DozerClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dozer.classmap.MappingDirection.ONE_WAY;

/**
 * <code>DozerTypeConverterLoader</code> provides the mechanism for registering
 * a Dozer {@link Mapper} as {@link TypeConverter} for a {@link CamelContext}.
 * <p/>
 * While a mapper can be explicitly supplied as a parameter the
 * {@link CamelContext}'s registry will also be searched for {@link Mapper}
 * instances. A {@link DozerTypeConverter} is created to wrap each
 * {@link Mapper} instance and the mapper is queried for the types it converts.
 * The queried types are used to register the {@link TypeConverter} with the
 * context via its {@link TypeConverterRegistry}.
 */
public class DozerTypeConverterLoader extends ServiceSupport implements CamelContextAware {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private CamelContext camelContext;
    private transient DozerBeanMapperConfiguration configuration;
    private transient Mapper mapper;

    /**
     * Creates a <code>DozerTypeConverter</code> performing no
     * {@link TypeConverter} registration.
     */
    public DozerTypeConverterLoader() {
    }

    /**
     * Creates a <code>DozerTypeConverter</code> that will search the given
     * {@link CamelContext} for instances of {@link Mapper}. Each
     * discovered instance will be wrapped as a {@link DozerTypeConverter} and
     * register as a {@link TypeConverter} with the context
     *
     * @param camelContext the context to register the
     *                     {@link DozerTypeConverter} in
     */
    public DozerTypeConverterLoader(CamelContext camelContext) {
        this.camelContext = camelContext;
        try {
            camelContext.addService(this);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    /**
     * Creates a <code>DozerTypeConverter</code> using the given
     * {@link DozerBeanMapperConfiguration} configuration.
     *
     * @param camelContext the context to register the
     *                     {@link DozerTypeConverter} in
     *
     * @param configuration dozer mapping bean configuration.
     */
    public DozerTypeConverterLoader(CamelContext camelContext, DozerBeanMapperConfiguration configuration) {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader appcl = camelContext.getApplicationContextClassLoader();
            if (appcl != null) {
                Thread.currentThread().setContextClassLoader(appcl);
            }
            log.info("Using DozerBeanMapperConfiguration: {}", configuration);
            MapperFactory factory = new MapperFactory(camelContext, configuration);
            Mapper mapper = factory.create();

            this.camelContext = camelContext;
            this.mapper = mapper;
            this.configuration = configuration;

            camelContext.addService(this);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    /**
     * Doses the actual querying and registration of {@link DozerTypeConverter}s
     * with the {@link CamelContext}.
     *
     * @param camelContext the context to register the
     *                     {@link DozerTypeConverter} in
     * @param mapper       the DozerMapperBean to be wrapped as a type converter.
     */
    public void init(CamelContext camelContext, Mapper mapper) {
        this.camelContext = camelContext;
        if (mapper != null) {
            this.mapper = mapper;
        }

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader appcl = camelContext.getApplicationContextClassLoader();
            if (appcl != null) {
                Thread.currentThread().setContextClassLoader(appcl);
            }

            Map<String, Mapper> mappers = lookupDozerBeanMappers();
            // only add if we do not already have it
            if (mapper != null && !mappers.containsValue(mapper)) {
                mappers.put("parameter", mapper);
            }

            // add any dozer bean mapper configurations
            Map<String, DozerBeanMapperConfiguration> configurations = lookupDozerBeanMapperConfigurations();
            if (configurations != null && configuration != null) {
                // filter out existing configuration, as we do not want to use it twice
                String key = null;
                for (Map.Entry<String, DozerBeanMapperConfiguration> entry : configurations.entrySet()) {
                    if (entry.getValue() == configuration) {
                        key = entry.getKey();
                        break;
                    }
                }
                if (key != null) {
                    configurations.remove(key);
                }
            }

            if (configurations != null) {
                for (Map.Entry<String, DozerBeanMapperConfiguration> entry : configurations.entrySet()) {
                    String id = entry.getKey();

                    MapperFactory factory = new MapperFactory(getCamelContext(), entry.getValue());
                    Mapper beanMapper = factory.create();

                    // only add if we do not already have it
                    if (!mappers.containsValue(beanMapper)) {
                        mappers.put(id, beanMapper);
                    }
                }
            }

            log.info("Loaded {} Dozer mappers from Camel registry.", mappers.size());

            if (mappers.size() == 0) {
                log.warn("No Dozer mappers found in Camel registry. You should add Dozer mappers as beans to the registry of the type: {}", Mapper.class.getName());
            }

            TypeConverterRegistry registry = camelContext.getTypeConverterRegistry();
            for (Map.Entry<String, Mapper> entry : mappers.entrySet()) {
                String mapperId = entry.getKey();
                Mapper dozer = entry.getValue();

                MappingMetadata meta = dozer.getMappingMetadata();

                List<ClassMappingMetadata> all = meta.getClassMappings();
                registerClassMaps(registry, mapperId, dozer, all);
            }

        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    /**
     * Lookup the dozer {@link Mapper} to be used.
     */
    protected Map<String, Mapper> lookupDozerBeanMappers() {
        return new HashMap<String, Mapper>(camelContext.getRegistry().findByTypeWithName(Mapper.class));
    }

    /**
     * Lookup the dozer {@link DozerBeanMapperConfiguration} to be used.
     */
    protected Map<String, DozerBeanMapperConfiguration> lookupDozerBeanMapperConfigurations() {
        return new HashMap<String, DozerBeanMapperConfiguration>(camelContext.getRegistry().findByTypeWithName(DozerBeanMapperConfiguration.class));
    }

    protected void registerClassMaps(TypeConverterRegistry registry, String dozerId, Mapper dozer, List<ClassMappingMetadata> all) {
        DozerTypeConverter converter = new DozerTypeConverter(dozer);
        for (ClassMappingMetadata map : all) {
            addDozerTypeConverter(registry, converter, dozerId, map.getSourceClass(), map.getDestinationClass());

            // if not one way then add the other way around also
            if (map.getMappingDirection() != ONE_WAY) {
                addDozerTypeConverter(registry, converter, dozerId, map.getDestinationClass(), map.getSourceClass());
            }
        }
    }

    protected void addDozerTypeConverter(TypeConverterRegistry registry, DozerTypeConverter converter,
                                         String dozerId, Class<?> to, Class<?> from) {
        if (log.isInfoEnabled()) {
            if (dozerId != null) {
                log.info("Added Dozer: {} as Camel type converter: {} -> {}", new Object[] {dozerId, from, to});
            } else {
                log.info("Added Dozer as Camel type converter: {} -> {}", new Object[] {from, to});
            }
        }

        registry.addTypeConverter(from, to, converter);
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Sets the {@link CamelContext} <b>and also</b> initializes this loader.
     * <p/>
     * The reason why {@link #init(org.apache.camel.CamelContext, org.dozer.Mapper)} is also called
     * is because making using Dozer in Spring XML files easier, as no need to use the init-method attribute.
     *
     * @param camelContext the CamelContext
     */
    public void setCamelContext(CamelContext camelContext) {
        if (this.camelContext == null) {
            this.camelContext = camelContext;
            try {
                camelContext.addService(this);
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    public Mapper getMapper() {
        return mapper;
    }

    public void setMapper(Mapper mapper) {
        this.mapper = mapper;
    }

    protected static URL loadMappingFile(ClassResolver classResolver, String mappingFile) {
        URL url = null;
        try {
            url = ResourceHelper.resolveResourceAsUrl(classResolver, mappingFile);
        } catch (MalformedURLException e) {
            // ignore
        }
        if (url == null) {
            // using the classloader of DozerClassLoader as a fallback
            url = DozerClassLoader.class.getClassLoader().getResource(mappingFile);
        }
        return url;
    }

    @Override
    protected void doStart() throws Exception {
        init(camelContext, mapper);
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
