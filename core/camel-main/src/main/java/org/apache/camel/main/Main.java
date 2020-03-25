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
package org.apache.camel.main;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.lw.LightweightCamelContext;
import org.apache.camel.spi.Registry;

/**
 * A Main class for booting up Camel in standalone mode.
 */
public class Main extends MainCommandLineSupport {

    protected static Main instance;
    protected final MainRegistry registry = new MainRegistry();

    public Main() {
    }

    public Main(Class... configurationClass) {
        super(configurationClass);
    }

    public static void main(String... args) throws Exception {
        Main main = new Main();
        instance = main;
        main.run(args);

        System.exit(main.getExitCode());
    }

    /**
     * Returns the currently executing main
     *
     * @return the current running instance
     */
    public static Main getInstance() {
        return instance;
    }

    /**
     * Binds the given <code>name</code> to the <code>bean</code> object, so
     * that it can be looked up inside the CamelContext this command line tool
     * runs with.
     * 
     * @param name the used name through which we do bind
     * @param bean the object to bind
     */
    public void bind(String name, Object bean) {
        registry.bind(name, bean);
    }

    /**
     * Using the given <code>name</code> does lookup for the bean being already
     * bound using the {@link #bind(String, Object)} method.
     * 
     * @see Registry#lookupByName(String)
     */
    public Object lookup(String name) {
        return registry.lookupByName(name);
    }

    /**
     * Using the given <code>name</code> and <code>type</code> does lookup for
     * the bean being already bound using the {@link #bind(String, Object)}
     * method.
     * 
     * @see Registry#lookupByNameAndType(String, Class)
     */
    public <T> T lookup(String name, Class<T> type) {
        return registry.lookupByNameAndType(name, type);
    }

    /**
     * Using the given <code>type</code> does lookup for the bean being already
     * bound using the {@link #bind(String, Object)} method.
     * 
     * @see Registry#findByTypeWithName(Class)
     */
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return registry.findByTypeWithName(type);
    }

    // Implementation methods
    // -------------------------------------------------------------------------


    @Override
    protected void doInit() throws Exception {
        super.doInit();
        initCamelContext();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (getCamelContext() != null) {
            try {
                // if we were veto started then mark as completed
                getCamelContext().start();
            } finally {
                if (getCamelContext().isVetoStarted()) {
                    completed();
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (getCamelContext() != null) {
            getCamelContext().stop();
        }
    }

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (getCamelContext() != null) {
            return getCamelContext().createProducerTemplate();
        } else {
            return null;
        }
    }

    @Override
    protected CamelContext createCamelContext() {
        if (mainConfigurationProperties.isLightweight()) {
            return new LightweightCamelContext(registry);
        } else {
            return new DefaultCamelContext(registry);
        }
    }

}
