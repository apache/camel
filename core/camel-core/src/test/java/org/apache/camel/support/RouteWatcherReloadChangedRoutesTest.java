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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25771: the summary of a reload says how many routes the reload changed, and marks them: a route that came back
 * with another checksum is changed, one that was not there before is new.
 */
public class RouteWatcherReloadChangedRoutesTest extends ContextTestSupport {

    @TempDir
    Path dir;

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private Appender appender;
    private NextRoutesLoader loader;
    private RouteWatcherReloadStrategy strategy;

    private static RouteBuilder routes(String ordersTarget, boolean audit) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders").routeId("orders").to(ordersTarget);
                from("direct:stock").routeId("stock").to("mock:stock");
                if (audit) {
                    from("direct:audit").routeId("audit").to("mock:audit");
                }
            }
        };
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return routes("mock:out", false);
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        loader = new NextRoutesLoader();
        loader.setCamelContext(context);
        context.getCamelContextExtension().addContextPlugin(RoutesLoader.class, loader);
        strategy = new RouteWatcherReloadStrategy();
        strategy.setCamelContext(context);
        strategy.start();

        appender = new AbstractAppender("reload-summary", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (RouteWatcherReloadStrategy.class.getName().equals(event.getLoggerName())) {
                    lines.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().addAppender(appender, Level.ALL, null);
        logging.updateLoggers();
    }

    @AfterEach
    public void stopCapture() {
        strategy.stop();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().removeAppender(appender.getName());
        logging.updateLoggers();
        appender.stop();
    }

    @Test
    public void theSummarySaysWhichRoutesTheReloadChanged() throws Exception {
        // the file now sends the orders elsewhere and has a new route; the stock route is as it was
        loader.next = routes("mock:other", true);

        strategy.onRouteReload(List.of(resource()), false);

        assertTrue(lines.contains("Routes reloaded summary (total:3 started:3 changed:2)"), lines.toString());
        assertTrue(line("orders").endsWith("(changed)"), line("orders"));
        assertTrue(line("audit").endsWith("(new)"), line("audit"));
        assertTrue(line("stock").endsWith(")") && !line("stock").contains("changed"), line("stock"));
        assertEquals(2, context.getRoute("orders").getRevision());
        assertEquals(1, context.getRoute("stock").getRevision());

        // saved again as it is: nothing changed
        lines.clear();
        strategy.onRouteReload(List.of(resource()), false);
        assertTrue(lines.contains("Routes reloaded summary (total:3 started:3 changed:0)"), lines.toString());
        assertEquals(2, context.getRoute("orders").getRevision());
    }

    private String line(String routeId) {
        return lines.stream().filter(l -> l.contains(" " + routeId + " (")).findFirst().orElse("");
    }

    private Resource resource() throws Exception {
        Path file = Files.writeString(dir.resolve("routes.yaml"), "- route: {}\n");
        return ResourceHelper.resolveMandatoryResource(context, "file:" + file);
    }

    // loads the next version of the routes, whatever the file has
    private static class NextRoutesLoader extends DefaultRoutesLoader {
        private RoutesBuilder next;

        @Override
        public Set<String> updateRoutes(Collection<Resource> resources) throws Exception {
            for (Route route : List.copyOf(getCamelContext().getRoutes())) {
                getCamelContext().getRouteController().stopRoute(route.getId());
                getCamelContext().removeRoute(route.getId());
            }
            getCamelContext().addRoutes(next);
            Set<String> ids = new LinkedHashSet<>();
            getCamelContext().getRoutes().forEach(r -> ids.add(r.getId()));
            return ids;
        }
    }
}
