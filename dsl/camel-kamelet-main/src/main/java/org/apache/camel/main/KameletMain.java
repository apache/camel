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
import java.util.Objects;

import groovy.lang.GroovyClassLoader;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;

/**
 * A Main class for booting up Camel with Kamelet in standalone mode.
 */
public class KameletMain extends MainCommandLineSupport {
    public static final String DEFAULT_KAMELETS_LOCATION = "classpath:/kamelets,github:apache:camel-kamelets/kamelets";

    private static ClassLoader kameletClassLoader;
    protected final MainRegistry registry = new MainRegistry();
    private boolean download = true;

    public KameletMain() {
        configureInitialProperties(DEFAULT_KAMELETS_LOCATION);
    }

    public KameletMain(String overrides) {
        Objects.requireNonNull(overrides);

        String locations = overrides + "," + DEFAULT_KAMELETS_LOCATION;

        configureInitialProperties(locations);
    }

    public static void main(String... args) throws Exception {
        KameletMain main = new KameletMain();
        int code = main.run(args);
        if (code != 0) {
            System.exit(code);
        }
        // normal exit
    }

    /**
     * Binds the given <code>name</code> to the <code>bean</code> object, so that it can be looked up inside the
     * CamelContext this command line tool runs with.
     *
     * @param name the used name through which we do bind
     * @param bean the object to bind
     */
    public void bind(String name, Object bean) {
        registry.bind(name, bean);
    }

    /**
     * Using the given <code>name</code> does lookup for the bean being already bound using the
     * {@link #bind(String, Object)} method.
     *
     * @see Registry#lookupByName(String)
     */
    public Object lookup(String name) {
        return registry.lookupByName(name);
    }

    /**
     * Using the given <code>name</code> and <code>type</code> does lookup for the bean being already bound using the
     * {@link #bind(String, Object)} method.
     *
     * @see Registry#lookupByNameAndType(String, Class)
     */
    public <T> T lookup(String name, Class<T> type) {
        return registry.lookupByNameAndType(name, type);
    }

    /**
     * Using the given <code>type</code> does lookup for the bean being already bound using the
     * {@link #bind(String, Object)} method.
     *
     * @see Registry#findByTypeWithName(Class)
     */
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return registry.findByTypeWithName(type);
    }

    public boolean isDownload() {
        return download;
    }

    /**
     * Whether to allow automatic downloaded JAR dependencies, over the internet, that Kamelets requires. This is by
     * default enabled.
     */
    public void setDownload(boolean download) {
        this.download = download;
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
                // if we were vetoed started then mark as completed
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
        // do not build/init camel context yet
        DefaultCamelContext answer = new DefaultCamelContext(false);
        if (kameletClassLoader == null) {
            kameletClassLoader = new GroovyClassLoader(KameletMain.class.getClassLoader());
        }
        answer.setApplicationContextClassLoader(kameletClassLoader);
        answer.setRegistry(registry);

        // embed HTTP server if port is specified
        Object port = getInitialProperties().get("camel.jbang.platform-http.port");
        if (port != null) {
            VertxHttpServer.registerServer(answer, Integer.parseInt(port.toString()));
        }

        // need to setup jfr early
        Object jfr = getInitialProperties().get("camel.jbang.jfr");
        Object jfrProfile = getInitialProperties().get("camel.jbang.jfr-profile");
        if ("jfr".equals(jfr) || jfrProfile != null) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRecording(true);
            if (jfrProfile != null) {
                recorder.setRecordingProfile(jfrProfile.toString());
            }
            answer.setStartupStepRecorder(recorder);
        }

        if (download) {
            try {
                // use resolver that can auto downloaded
                answer.setComponentResolver(new DependencyDownloaderComponentResolver(answer));
                answer.setDataFormatResolver(new DependencyDownloaderDataFormatResolver(answer));
                answer.setLanguageResolver(new DependencyDownloaderLanguageResolver(answer));
                answer.setRoutesLoader(new DependencyDownloaderRoutesLoader());
                answer.addService(new DependencyDownloaderKamelet());
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }

        return answer;
    }

    /**
     * Sets initial properties that are specific to camel-kamelet-main
     */
    protected void configureInitialProperties(String location) {
        addInitialProperty("camel.component.kamelet.location", location);
    }

}
