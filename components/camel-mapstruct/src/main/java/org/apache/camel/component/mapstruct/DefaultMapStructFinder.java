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
package org.apache.camel.component.mapstruct;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.SimpleTypeConverter;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ReflectionHelper;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMapStructFinder extends ServiceSupport implements MapStructMapperFinder, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMapStructFinder.class);

    private CamelContext camelContext;
    private String mapperPackageName;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getMapperPackageName() {
        return mapperPackageName;
    }

    @Override
    public int discoverMappings(Class<?> clazz) {
        final AtomicInteger answer = new AtomicInteger();
        try {
            // is there a generated mapper
            final Object mapper = Mappers.getMapper(clazz);
            if (mapper != null) {
                ReflectionHelper.doWithMethods(mapper.getClass(), mc -> {
                    // must be public
                    if (!Modifier.isPublic(mc.getModifiers())) {
                        return;
                    }
                    // must not be a default method
                    if (mc.isDefault()) {
                        return;
                    }
                    // must have a single parameter
                    int parameterCount = mc.getParameterCount();
                    if (parameterCount != 1) {
                        return;
                    }
                    Class<?> from = mc.getParameterTypes()[0];
                    // must return a non-primitive value
                    Class<?> to = mc.getReturnType();
                    if (to.isPrimitive()) {
                        return;
                    }
                    // okay register this method as a Camel type converter
                    camelContext.getTypeConverterRegistry()
                            .addTypeConverter(to, from, new SimpleTypeConverter(
                                    false, (type, exchange, value) -> ObjectHelper.invokeMethod(mc, mapper, value)));
                    LOG.debug("Added MapStruct type converter: {} -> {}", from, to);
                    answer.incrementAndGet();
                });
            }
        } catch (Exception e) {
            LOG.debug("Mapper class: {} is not a MapStruct Mapper. Skipping this class.", clazz);
        }

        return answer.get();
    }

    public void setMapperPackageName(String mapperPackageName) {
        this.mapperPackageName = mapperPackageName;
    }

    @Override
    protected void doInit() throws Exception {
        if (mapperPackageName != null) {
            String[] names = mapperPackageName.split(",");
            ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
            var set = PluginHelper.getPackageScanClassResolver(ecc)
                    .findByFilter(f -> f.getName().endsWith("Mapper"), names);
            if (!set.isEmpty()) {
                int converters = 0;
                for (Class<?> clazz : set) {
                    converters += discoverMappings(clazz);
                }
                LOG.info("Discovered {} MapStruct type converters from classpath scanning: {}", converters, mapperPackageName);
            }
        }
    }

}
