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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.support.ExtendedRouteBuilderLoaderSupport;
import org.apache.camel.spi.CompilePostProcessor;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceAware;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.dsl.java.joor.Helper.determineName;

@ManagedResource(description = "Managed JavaRoutesBuilderLoader")
@RoutesLoader(JavaRoutesBuilderLoader.EXTENSION)
public class JavaRoutesBuilderLoader extends ExtendedRouteBuilderLoaderSupport {

    public static final String EXTENSION = "java";

    private static final Logger LOG = LoggerFactory.getLogger(JavaRoutesBuilderLoader.class);

    public JavaRoutesBuilderLoader() {
        super(EXTENSION);
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();

        // register jOOR classloader to camel, so we are able to load classes we have compiled
        CamelContext context = getCamelContext();
        if (context != null) {
            JavaJoorClassLoader cl = new JavaJoorClassLoader();
            context.getClassResolver().addClassLoader(cl);
            addCompilePostProcessor(cl);
        }
    }

    @Override
    protected RouteBuilder doLoadRouteBuilder(Resource resource) throws Exception {
        Collection<RoutesBuilder> answer = doLoadRoutesBuilders(List.of(resource));
        if (answer.size() == 1) {
            RoutesBuilder builder = answer.iterator().next();
            return (RouteBuilder) builder;
        }

        return super.doLoadRouteBuilder(resource);
    }

    @Override
    protected Collection<RoutesBuilder> doLoadRoutesBuilders(Collection<Resource> resources) throws Exception {
        Collection<RoutesBuilder> answer = new ArrayList<>();

        LOG.debug("Loading .java resources from: {}", resources);

        CompilationUnit unit = CompilationUnit.input();

        Map<String, Resource> nameToResource = new HashMap<>();
        for (Resource resource : resources) {
            try (InputStream is = resourceInputStream(resource)) {
                if (is == null) {
                    throw new FileNotFoundException(resource.getLocation());
                }
                String content = IOHelper.loadText(is);
                String name = determineName(resource, content);
                unit.addClass(name, content);
                nameToResource.put(name, resource);
            }
        }

        LOG.debug("Compiling unit: {}", unit);
        CompilationUnit.Result result = MultiCompile.compileUnit(unit);

        // remember the last loaded resource-set if route reloading is enabled
        if (getCamelContext().hasService(RouteWatcherReloadStrategy.class) != null) {
            getCamelContext().getRegistry().bind(RouteWatcherReloadStrategy.RELOAD_RESOURCES, nameToResource.values());
        }

        for (String className : result.getClassNames()) {
            Object obj = null;

            Class<?> clazz = result.getClass(className);
            if (clazz != null) {
                boolean skip = clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())
                        || Modifier.isPrivate(clazz.getModifiers());
                // must have a default no-arg constructor to be able to create an instance
                boolean ctr = ObjectHelper.hasDefaultNoArgConstructor(clazz);
                if (ctr && !skip) {
                    // create a new instance of the class
                    try {
                        obj = getCamelContext().getInjector().newInstance(clazz);
                        if (obj != null) {
                            LOG.debug("Compiled: {} -> {}", className, obj);

                            // inject context and resource
                            CamelContextAware.trySetCamelContext(obj, getCamelContext());
                            ResourceAware.trySetResource(obj, nameToResource.get(className));
                        }
                    } catch (Exception e) {
                        throw new RuntimeCamelException("Cannot create instance of class: " + className, e);
                    }
                }
            }

            // support custom annotation scanning post compilation
            // such as to register custom beans, type converters, etc.
            for (CompilePostProcessor pre : getCompilePostProcessors()) {
                byte[] byteCode = result.getByteCode(className);
                pre.postCompile(getCamelContext(), className, clazz, byteCode, obj);
            }

            if (obj instanceof RouteBuilder) {
                RouteBuilder builder = (RouteBuilder) obj;
                answer.add(builder);
            }
        }

        return answer;
    }

}
