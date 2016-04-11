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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.dozer.DozerComponent;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.dozer.classmap.ClassMap;
import org.dozer.classmap.MappingFileData;
import org.dozer.loader.api.BeanMappingBuilder;
import org.dozer.loader.xml.MappingFileReader;
import org.dozer.loader.xml.XMLParserFactory;
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
    private transient DozerBeanMapper mapper;

    /**
     * Creates a <code>DozerTypeConverter</code> performing no
     * {@link TypeConverter} registration.
     */
    public DozerTypeConverterLoader() {
    }

    /**
     * Creates a <code>DozerTypeConverter</code> that will search the given
     * {@link CamelContext} for instances of {@link DozerBeanMapper}. Each
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
            DozerBeanMapper mapper = createDozerBeanMapper(configuration);

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
     * Creates a <code>DozerTypeConverter</code> that will wrap the the given
     * {@link DozerBeanMapper} as a {@link DozerTypeConverter} and register it
     * with the given context. It will also search the context for
     *
     * @param camelContext the context to register the
     *                     {@link DozerTypeConverter} in
     * @param mapper       the DozerMapperBean to be wrapped as a type converter.
     */
    @Deprecated
    public DozerTypeConverterLoader(CamelContext camelContext, DozerBeanMapper mapper) {
        this.camelContext = camelContext;
        this.mapper = mapper;

        try {
            camelContext.addService(this);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
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
    public void init(CamelContext camelContext, DozerBeanMapper mapper) {
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

            Map<String, DozerBeanMapper> mappers = lookupDozerBeanMappers();
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
                if (configurations.size() > 1) {
                    log.warn("Loaded " + configurations.size() + " Dozer configurations from Camel registry."
                            + " Dozer is most efficient when there is a single mapper instance. Consider amalgamating instances.");
                }
                for (Map.Entry<String, DozerBeanMapperConfiguration> entry : configurations.entrySet()) {
                    String id = entry.getKey();
                    DozerBeanMapper beanMapper = createDozerBeanMapper(entry.getValue());
                    // only add if we do not already have it
                    if (!mappers.containsValue(beanMapper)) {
                        mappers.put(id, beanMapper);
                    }
                }
            }

            if (mappers.size() > 1) {
                log.warn("Loaded " + mappers.size() + " Dozer mappers from Camel registry."
                        + " Dozer is most efficient when there is a single mapper instance. Consider amalgamating instances.");
            } else if (mappers.size() == 0) {
                log.warn("No Dozer mappers found in Camel registry. You should add Dozer mappers as beans to the registry of the type: "
                        + DozerBeanMapper.class.getName());
            }


            TypeConverterRegistry registry = camelContext.getTypeConverterRegistry();
            for (Map.Entry<String, DozerBeanMapper> entry : mappers.entrySet()) {
                String mapperId = entry.getKey();
                DozerBeanMapper dozer = entry.getValue();
                List<ClassMap> all = loadMappings(camelContext, mapperId, dozer);
                registerClassMaps(registry, mapperId, dozer, all);
            }

        } finally {
            Thread.currentThread().setContextClassLoader(tccl);
        }
    }

    /**
     * Creates a {@link DozerBeanMapper} from the given configuration.
     *
     * @param configuration  the dozer bean mapper configuration.
     * @return the created mapper
     */
    public static DozerBeanMapper createDozerBeanMapper(DozerBeanMapperConfiguration configuration) {
        DozerBeanMapper mapper;
        if (configuration.getMappingFiles() != null) {
            mapper = DozerComponent.createDozerBeanMapper(configuration.getMappingFiles());
        } else {
            mapper = DozerComponent.createDozerBeanMapper(Collections.<String>emptyList());
        }
        if (configuration.getCustomConverters() != null) {
            mapper.setCustomConverters(configuration.getCustomConverters());
        }
        if (configuration.getEventListeners() != null) {
            mapper.setEventListeners(configuration.getEventListeners());
        }
        if (configuration.getCustomConvertersWithId() != null) {
            mapper.setCustomConvertersWithId(configuration.getCustomConvertersWithId());
        }
        if (configuration.getCustomFieldMapper() != null) {
            mapper.setCustomFieldMapper(configuration.getCustomFieldMapper());
        }
        return mapper;
    }

    /**
     * Lookup the dozer {@link DozerBeanMapper} to be used.
     */
    protected Map<String, DozerBeanMapper> lookupDozerBeanMappers() {
        return new HashMap<String, DozerBeanMapper>(camelContext.getRegistry().findByTypeWithName(DozerBeanMapper.class));
    }

    /**
     * Lookup the dozer {@link DozerBeanMapperConfiguration} to be used.
     */
    protected Map<String, DozerBeanMapperConfiguration> lookupDozerBeanMapperConfigurations() {
        return new HashMap<String, DozerBeanMapperConfiguration>(camelContext.getRegistry().findByTypeWithName(DozerBeanMapperConfiguration.class));
    }

    protected void registerClassMaps(TypeConverterRegistry registry, String dozerId, DozerBeanMapper dozer, List<ClassMap> all) {
        DozerTypeConverter converter = new DozerTypeConverter(dozer);
        for (ClassMap map : all) {
            addDozerTypeConverter(registry, converter, dozerId, map.getSrcClassToMap(), map.getDestClassToMap());
            // if not one way then add the other way around also
            if (map.getType() != ONE_WAY) {
                addDozerTypeConverter(registry, converter, dozerId, map.getDestClassToMap(), map.getSrcClassToMap());
            }
        }
    }

    protected void addDozerTypeConverter(TypeConverterRegistry registry, DozerTypeConverter converter,
                                         String dozerId, Class<?> to, Class<?> from) {
        if (log.isInfoEnabled()) {
            if (dozerId != null) {
                log.info("Added Dozer: {} as Camel type converter: {} -> {}", new Object[]{dozerId, from, to});
            } else {
                log.info("Added Dozer as Camel type converter: {} -> {}", new Object[]{from, to});
            }
        }
        registry.addTypeConverter(from, to, converter);
    }

    private List<ClassMap> loadMappings(CamelContext camelContext, String mapperId, DozerBeanMapper mapper) {
        List<ClassMap> answer = new ArrayList<ClassMap>();

        // load the class map using the class resolver so we can load from classpath in OSGi
        MappingFileReader reader = new MappingFileReader(XMLParserFactory.getInstance());
        List<String> mappingFiles = mapper.getMappingFiles();
        if (mappingFiles == null) {
            return Collections.emptyList();
        }

        for (String name : mappingFiles) {
            URL url = loadMappingFile(camelContext.getClassResolver(), name);
            if (url != null) {
                MappingFileData data = reader.read(url);
                answer.addAll(data.getClassMaps());
            }
        }

        return answer;
    }

    /**
     * Registers Dozer <code>BeanMappingBuilder</code> in current mapper instance.
     * This method should be called instead of direct <code>mapper.addMapping()</code> invocation for Camel
     * being able to register given type conversion.
     *
     * @param beanMappingBuilder api-based mapping builder
     */
    public void addMapping(BeanMappingBuilder beanMappingBuilder) {
        if (mapper == null) {
            log.warn("No mapper instance provided to " + this.getClass().getSimpleName()
                    + ". Mapping has not been registered!");
            return;
        }

        mapper.addMapping(beanMappingBuilder);
        MappingFileData mappingFileData = beanMappingBuilder.build();
        TypeConverterRegistry registry = camelContext.getTypeConverterRegistry();
        List<ClassMap> classMaps = new ArrayList<ClassMap>();
        classMaps.addAll(mappingFileData.getClassMaps());
        registerClassMaps(registry, null, mapper, classMaps);
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Sets the {@link CamelContext} <b>and also</b> initializes this loader.
     * <p/>
     * The reason why {@link #init(org.apache.camel.CamelContext, org.dozer.DozerBeanMapper)} is also called
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

    public DozerBeanMapper getMapper() {
        return mapper;
    }

    public void setMapper(DozerBeanMapper mapper) {
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
