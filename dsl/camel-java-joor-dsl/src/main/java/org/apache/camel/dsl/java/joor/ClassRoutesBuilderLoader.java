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
package org.apache.camel.dsl.java.joor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.ExtendedRouteBuilderLoaderSupport;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed ClassRoutesBuilderLoader")
@RoutesLoader(ClassRoutesBuilderLoader.EXTENSION)
public class ClassRoutesBuilderLoader extends ExtendedRouteBuilderLoaderSupport {

    public static final String EXTENSION = "class";

    private static final Logger LOG = LoggerFactory.getLogger(ClassRoutesBuilderLoader.class);

    public ClassRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    protected Collection<RoutesBuilder> doLoadRoutesBuilders(Collection<Resource> resources) throws Exception {
        Collection<RoutesBuilder> answer = new ArrayList<>();

        LOG.debug("Loading .class resources from: {}", resources);

        // load all the byte code first from the resources
        Map<String, byte[]> byteCodes = new LinkedHashMap<>();
        for (Resource res : resources) {
            String className = asClassName(res);
            InputStream is = res.getInputStream(); // load resource as-is
            if (is != null) {
                byte[] code = is.readAllBytes();
                byteCodes.put(className, code);
            }
        }

        ByteArrayClassLoader cl = new ByteArrayClassLoader(byteCodes);

        // instantiate classes from the byte codes
        for (Resource res : resources) {
            String className = asClassName(res);
            Class<?> clazz = cl.findClass(className);

            Object obj;
            try {
                // requires a default no-arg constructor otherwise we skip the class
                obj = getCamelContext().getInjector().newInstance(clazz);
            } catch (Exception e) {
                LOG.debug("Loaded class {} must have a default no-arg constructor. Skipping.", className);
                continue;
            }

            // inject context and resource
            CamelContextAware.trySetCamelContext(obj, getCamelContext());
            ResourceAware.trySetResource(obj, res);

            // support custom annotation scanning post compilation
            // such as to register custom beans, type converters, etc.
            for (CompilePostProcessor pre : getCompilePostProcessors()) {
                // do not pass in byte code as we do not need to write to disk again
                pre.postCompile(getCamelContext(), className, clazz, null, obj);
            }

            if (obj instanceof RouteBuilder) {
                RouteBuilder builder = (RouteBuilder) obj;
                answer.add(builder);
            }
        }

        return answer;
    }

    private static String asClassName(Resource resource) {
        String className = resource.getLocation();
        if (className.contains(":")) {
            // remove scheme such as classpath:foo.class
            className = StringHelper.after(className, ":");
        }
        className = className.replace('/', '.');
        if (className.endsWith(".class")) {
            className = className.substring(0, className.length() - 6);
        }
        return className;
    }
}
