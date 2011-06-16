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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.TypeConverterRegistry;
import org.dozer.DozerBeanMapper;
import org.dozer.Mapper;
import org.dozer.classmap.ClassMap;
import org.dozer.classmap.MappingFileData;
import org.dozer.config.BeanContainer;
import org.dozer.loader.api.BeanMappingBuilder;
import org.dozer.loader.xml.MappingFileReader;
import org.dozer.loader.xml.XMLParserFactory;
import org.dozer.util.DozerClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class DozerTypeConverterLoader implements CamelContextAware {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private CamelContext camelContext;
    private DozerBeanMapper mapper;

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
        init(camelContext, null);
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
    public DozerTypeConverterLoader(CamelContext camelContext, DozerBeanMapper mapper) {
        init(camelContext, mapper);
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
        this.mapper = mapper;

        CamelToDozerClassResolverAdapter adapter = new CamelToDozerClassResolverAdapter(camelContext);
        BeanContainer.getInstance().setClassLoader(adapter);
        
        Map<String, DozerBeanMapper> mappers = new HashMap<String, DozerBeanMapper>(camelContext.getRegistry().lookupByType(DozerBeanMapper.class));
        if (mapper != null) {
            mappers.put("parameter", mapper);
        }
        if (mappers.size() > 1) {
            log.warn("Loaded " + mappers.size() + " Dozer mappers from Camel registry."
                    + " Dozer is most efficient when there is a single mapper instance. Consider amalgamating instances.");
        } else if (mappers.size() == 0) {
            log.warn("No Dozer mappers found in Camel registry. You should add Dozer mappers as beans to the registry of the type: "
                    + DozerBeanMapper.class.getName());
        }

        TypeConverterRegistry registry = camelContext.getTypeConverterRegistry();
        for (DozerBeanMapper dozer : mappers.values()) {
            List<ClassMap> all = loadMappings(camelContext, dozer);
            registerClassMaps(registry, dozer, all);
        }
    }

    private void registerClassMaps(TypeConverterRegistry registry, DozerBeanMapper dozer, List<ClassMap> all) {
        DozerTypeConverter converter = new DozerTypeConverter(dozer);
        for (ClassMap map : all) {
            if (log.isInfoEnabled()) {
                log.info("Added {} -> {} as type converter to: {}", new Object[]{map.getSrcClassName(), map.getDestClassName(), registry});
            }
            registry.addTypeConverter(map.getSrcClassToMap(), map.getDestClassToMap(), converter);
            registry.addTypeConverter(map.getDestClassToMap(), map.getSrcClassToMap(), converter);
        }
    }

    private List<ClassMap> loadMappings(CamelContext camelContext, DozerBeanMapper mapper) {
        List<ClassMap> answer = new ArrayList<ClassMap>();

        // load the class map using the class resolver so we can load from classpath in OSGi
        MappingFileReader reader = new MappingFileReader(XMLParserFactory.getInstance());
        List<String> mappingFiles = mapper.getMappingFiles();
        if (mappingFiles == null) {
            return Collections.emptyList();
        }

        for (String name : mappingFiles) {
            URL url = camelContext.getClassResolver().loadResourceAsURL(name);
            MappingFileData data = reader.read(url);
            answer.addAll(data.getClassMaps());
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
        ArrayList<ClassMap> classMaps = new ArrayList<ClassMap>();
        classMaps.addAll(mappingFileData.getClassMaps());
        registerClassMaps(registry, mapper, classMaps);
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        init(camelContext, null);
    }

    private static final class CamelToDozerClassResolverAdapter implements DozerClassLoader {

        private final ClassResolver classResolver;

        private CamelToDozerClassResolverAdapter(CamelContext camelContext) {
            classResolver = camelContext.getClassResolver();
        }

        public Class<?> loadClass(String s) {
            return classResolver.resolveClass(s);
        }

        public URL loadResource(String s) {
            URL url = classResolver.loadResourceAsURL(s);
            if (url == null) {
                // using the classloader of DozerClassLoader as a fallback
                url = DozerClassLoader.class.getClassLoader().getResource(s);
            } 
            return url;
        }
    }

}
