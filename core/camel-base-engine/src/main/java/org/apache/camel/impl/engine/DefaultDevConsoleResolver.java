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
package org.apache.camel.impl.engine;

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.support.service.ServiceSupport;

/**
 * Default dev console resolver that looks for dev consoles factories in
 * <b>META-INF/services/org/apache/camel/dev-console/</b>.
 */
public class DefaultDevConsoleResolver extends ServiceSupport implements DevConsoleResolver, CamelContextAware {

    public static final String DEV_CONSOLE_RESOURCE_PATH = "META-INF/services/org/apache/camel/dev-console/";

    protected FactoryFinder devConsoleFactory;
    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public DevConsole resolveDevConsole(String id) {
        // lookup in registry first
        DevConsole answer = camelContext.getRegistry().lookupByNameAndType(id + "-dev-console", DevConsole.class);
        if (answer == null) {
            answer = camelContext.getRegistry().lookupByNameAndType(id, DevConsole.class);
        }
        if (answer != null) {
            return answer;
        }

        Class<?> type = null;
        try {
            type = findDevConsole(id, camelContext);
        } catch (NoFactoryAvailableException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URI, no DevConsole registered for id: " + id, e);
        }

        if (type != null) {
            if (DevConsole.class.isAssignableFrom(type)) {
                answer = (DevConsole) camelContext.getInjector().newInstance(type, false);
                CamelContextAware.trySetCamelContext(answer, camelContext);
            } else {
                throw new IllegalArgumentException(
                        "Resolving dev-console: " + id + " detected type conflict: Not a DevConsole implementation. Found: "
                                                   + type.getName());
            }
        }

        return answer;
    }

    protected Class<?> findDevConsole(String name, CamelContext context) throws Exception {
        if (devConsoleFactory == null) {
            devConsoleFactory = context.getCamelContextExtension().getFactoryFinder(DEV_CONSOLE_RESOURCE_PATH);
        }
        return devConsoleFactory.findOptionalClass(name).orElse(null);
    }

    @Override
    public Optional<DevConsole> lookupDevConsole(String id) {
        DevConsoleRegistry dcr = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class);
        if (dcr != null) {
            return dcr.getConsole(id);
        } else {
            return Optional.empty();
        }
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        if (devConsoleFactory != null) {
            devConsoleFactory.clear();
        }
    }
}
