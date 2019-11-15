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
package org.apache.camel.websocket.jsr356;

import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.server.ServerContainer;

import static java.util.Optional.ofNullable;

public class ServletIntegration implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) {
        ctx.addListener(new ServletContextListener() {
            @Override
            public void contextInitialized(final ServletContextEvent sce) {
                final String contextPath = sce.getServletContext().getContextPath();
                ofNullable(sce.getServletContext().getAttribute(ServerContainer.class.getName())).map(ServerContainer.class::cast)
                    .ifPresent(container -> JSR356WebSocketComponent.registerServer(contextPath, container));
            }

            @Override
            public void contextDestroyed(final ServletContextEvent sce) {
                JSR356WebSocketComponent.unregisterServer(sce.getServletContext().getContextPath());
            }
        });
    }
}
