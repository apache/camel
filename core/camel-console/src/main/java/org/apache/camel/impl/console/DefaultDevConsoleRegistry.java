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
package org.apache.camel.impl.console;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.console.DevConsoleResolver;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link org.apache.camel.console.DevConsoleRegistry}.
 */
@org.apache.camel.spi.annotations.DevConsole(DevConsoleRegistry.NAME)
@DeferredContextBinding
public class DefaultDevConsoleRegistry extends ServiceSupport implements DevConsoleRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDevConsoleRegistry.class);

    private String id = "camel-console";
    private CamelContext camelContext;
    private final Set<DevConsole> consoles;
    private boolean enabled = true;
    private volatile boolean loadDevConsolesDone;

    public DefaultDevConsoleRegistry() {
        this(null);
    }

    public DefaultDevConsoleRegistry(CamelContext camelContext) {
        // sort by id
        this.consoles = new TreeSet<>(Comparator.comparing(DevConsole::getId));
        setCamelContext(camelContext);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    protected void doInit() throws Exception {
        for (DevConsole console : consoles) {
            CamelContextAware.trySetCamelContext(console, camelContext);
            ServiceHelper.initService(console);
        }
    }

    @Override
    protected void doStart() throws Exception {
        for (DevConsole console : consoles) {
            ServiceHelper.startService(console);
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (DevConsole console : consoles) {
            ServiceHelper.stopService(console);
        }
    }

    @Override
    public DevConsole resolveById(String id) {
        DevConsole answer = consoles.stream().filter(h -> h.getId().equals(id)).findFirst()
                .orElse(camelContext.getRegistry().findByTypeWithName(DevConsole.class).get(id));
        if (answer == null) {
            DevConsoleResolver resolver = PluginHelper.getDevConsoleResolver(camelContext);
            answer = resolver.resolveDevConsole(id);
            if (answer != null) {
                register(answer);
            }
        }

        return answer;
    }

    @Override
    public boolean register(DevConsole console) {
        boolean result;
        // do we have this already
        if (getConsole(console.getId()).isPresent()) {
            return false;
        }
        result = consoles.add(console);
        if (result) {
            CamelContextAware.trySetCamelContext(console, camelContext);
            // ensure console is started as it may be registered later
            ServiceHelper.startService(console);
            LOG.debug("DevConsole with id {} successfully registered", console.getId());
        }
        return result;
    }

    @Override
    public boolean unregister(DevConsole console) {
        boolean result;

        result = consoles.remove(console);
        if (result) {
            LOG.debug("DevConsole with id {} successfully un-registered", console.getId());
        }
        return result;
    }

    @Override
    public Stream<DevConsole> stream() {
        if (enabled) {
            return consoles.stream();
        }
        return Stream.empty();
    }

    @Override
    public void loadDevConsoles() {
        loadDevConsoles(false);
    }

    @Override
    public void loadDevConsoles(boolean force) {
        StopWatch watch = new StopWatch();

        if (!loadDevConsolesDone || force) {
            loadDevConsolesDone = true;

            DefaultDevConsolesLoader loader = new DefaultDevConsolesLoader(camelContext);
            Collection<DevConsole> col = loader.loadDevConsoles(force);

            if (!col.isEmpty()) {
                int added = 0;
                // register the loaded consoles
                for (DevConsole console : col) {
                    if (register(console)) {
                        added++;
                    }
                }
                String time = TimeUtils.printDuration(watch.taken(), true);
                LOG.debug("Dev consoles (scanned: {} registered: {}) loaded in {}", col.size(), added, time);
            }
        }
    }
}
