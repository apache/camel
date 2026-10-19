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
package org.apache.camel.support;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25831: a reload with a Java source loads the files that declare beans again. The file of a bean that was
 * deleted (such as a Java class moved to another folder) failed that reload, and every reload after it.
 */
public class RouteWatcherReloadDeletedBeanSourceTest extends ContextTestSupport {

    @TempDir
    Path dir;

    private RecordingRoutesLoader loader;
    private RouteWatcherReloadStrategy strategy;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        loader = new RecordingRoutesLoader();
        loader.setCamelContext(context);
        context.getCamelContextExtension().addContextPlugin(RoutesLoader.class, loader);
        strategy = new RouteWatcherReloadStrategy();
        strategy.setCamelContext(context);
    }

    @Test
    public void theFileOfABeanThatIsGoneIsNotLoadedAgain() throws Exception {
        Resource changed = file("Changed.java", "class Changed {}");
        Resource bean = file("Greeter.java", "class Greeter {}");
        Resource gone = file("Gone.java", null);
        strategy.setBeanResources(() -> List.of(bean, gone));

        strategy.onRouteReload(List.of(changed), false);
        // and the next reload too
        strategy.onRouteReload(List.of(changed), false);

        assertEquals(List.of(changed.getLocation(), bean.getLocation(), changed.getLocation(), bean.getLocation()),
                loader.loaded);
    }

    @Test
    public void aReloadResourceThatIsGoneIsNotLoadedAgain() throws Exception {
        Resource changed = file("Changed.java", "class Changed {}");
        Resource extra = file("extra.properties", "a = 1");
        Resource gone = file("gone.properties", null);
        context.getRegistry().bind(RouteWatcherReloadStrategy.RELOAD_RESOURCES, List.of(extra, gone));

        strategy.onRouteReload(List.of(changed), false);

        assertEquals(List.of(changed.getLocation(), extra.getLocation()), loader.loaded);
    }

    // a file with the content, or a file that is gone when the content is null
    private Resource file(String name, String content) throws Exception {
        Path path = dir.resolve(name);
        if (content != null) {
            Files.writeString(path, content);
        }
        return ResourceHelper.resolveResource(context, "file:" + path);
    }

    // loads no routes: it records the files, and fails on a file that is gone, as the loaders do
    private static class RecordingRoutesLoader extends DefaultRoutesLoader {
        private final List<String> loaded = new ArrayList<>();

        @Override
        public Set<String> updateRoutes(Collection<Resource> resources) throws Exception {
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    throw new FileNotFoundException(resource.getLocation() + " does not exist");
                }
                loaded.add(resource.getLocation());
            }
            return Set.of();
        }
    }
}
