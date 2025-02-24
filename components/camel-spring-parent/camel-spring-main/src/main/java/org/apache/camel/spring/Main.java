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
package org.apache.camel.spring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.main.MainCommandLineSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * A command line tool for booting up a CamelContext using an optional Spring
 * {@link org.springframework.context.ApplicationContext}.
 * <p/>
 * By placing a file in the {@link #LOCATION_PROPERTIES} directory of any JARs on the classpath, allows this Main class
 * to load those additional Spring XML files as Spring {@link org.springframework.context.ApplicationContext} to be
 * included.
 * <p/>
 * Each line in the {@link #LOCATION_PROPERTIES} is a reference to a Spring XML file to include, which by default gets
 * loaded from classpath.
 */
public class Main extends MainCommandLineSupport {

    public static final String LOCATION_PROPERTIES = "META-INF/camel-spring/location.properties";
    protected static Main instance;
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    private String applicationContextUri = "META-INF/spring/*.xml";
    private String fileApplicationContextUri;
    private AbstractApplicationContext applicationContext;
    private AbstractApplicationContext parentApplicationContext;
    private AbstractApplicationContext additionalApplicationContext;
    private String parentApplicationContextUri;
    private boolean allowMultipleCamelContexts;

    public Main() {
        // do not run in standalone mode as we let Spring create and manage CamelContext but use this Main to bootstrap
        standalone = false;
    }

    @Override
    protected void initOptions() {
        super.initOptions();

        addOption(new ParameterOption(
                "ac", "applicationContext",
                "Sets the classpath based spring ApplicationContext", "applicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setApplicationContextUri(parameter);
            }
        });

        addOption(new ParameterOption(
                "fa", "fileApplicationContext",
                "Sets the filesystem based spring ApplicationContext", "fileApplicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setFileApplicationContextUri(parameter);
            }
        });
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

    // Properties
    // -------------------------------------------------------------------------
    public AbstractApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void setApplicationContext(AbstractApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public String getApplicationContextUri() {
        return applicationContextUri;
    }

    public void setApplicationContextUri(String applicationContextUri) {
        this.applicationContextUri = applicationContextUri;
    }

    public String getFileApplicationContextUri() {
        return fileApplicationContextUri;
    }

    public void setFileApplicationContextUri(String fileApplicationContextUri) {
        this.fileApplicationContextUri = fileApplicationContextUri;
    }

    public AbstractApplicationContext getParentApplicationContext() {
        if (parentApplicationContext == null) {
            if (parentApplicationContextUri != null) {
                parentApplicationContext = new ClassPathXmlApplicationContext(parentApplicationContextUri);
                parentApplicationContext.start();
            }
        }
        return parentApplicationContext;
    }

    public void setParentApplicationContext(AbstractApplicationContext parentApplicationContext) {
        this.parentApplicationContext = parentApplicationContext;
    }

    public String getParentApplicationContextUri() {
        return parentApplicationContextUri;
    }

    public void setParentApplicationContextUri(String parentApplicationContextUri) {
        this.parentApplicationContextUri = parentApplicationContextUri;
    }

    public boolean isAllowMultipleCamelContexts() {
        return allowMultipleCamelContexts;
    }

    /**
     * Enable this to allow multiple CamelContexts to be loaded by this Main class. By default only a single
     * CamelContext is allowed.
     */
    public void setAllowMultipleCamelContexts(boolean allowMultipleCamelContexts) {
        this.allowMultipleCamelContexts = allowMultipleCamelContexts;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    protected CamelContext createCamelContext() {
        Map<String, SpringCamelContext> camels = applicationContext.getBeansOfType(SpringCamelContext.class);
        if (camels.size() > 1) {
            if (isAllowMultipleCamelContexts()) {
                // just grab the first
                return camels.values().iterator().next();
            }
            throw new IllegalArgumentException(
                    "Multiple CamelContext detected. Set allowMultipleCamelContexts=true to allow multiple CamelContexts");
        } else if (camels.size() == 1) {
            return camels.values().iterator().next();
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        try {
            super.doStart();
            if (applicationContext == null) {
                applicationContext = createDefaultApplicationContext();
            }

            // then start any additional after Camel has been started
            if (additionalApplicationContext == null) {
                additionalApplicationContext = createAdditionalLocationsFromClasspath();
                if (additionalApplicationContext != null) {
                    LOG.debug("Starting Additional ApplicationContext: {}", additionalApplicationContext.getId());
                    additionalApplicationContext.start();
                }
            }

            LOG.debug("Starting Spring ApplicationContext: {}", applicationContext.getId());
            applicationContext.start();

            initCamelContext();
        } catch (Exception e) {
            // if we were veto started then mark as completed
            VetoCamelContextStartException veto = ObjectHelper.getException(VetoCamelContextStartException.class, e);
            if (veto != null) {
                completed();
            } else {
                throw e;
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        try {
            if (additionalApplicationContext != null) {
                LOG.debug("Stopping Additional ApplicationContext: {}", additionalApplicationContext.getId());
                additionalApplicationContext.stop();
            }
            if (applicationContext != null) {
                LOG.debug("Stopping Spring ApplicationContext: {}", applicationContext.getId());
                applicationContext.stop();
            }
            IOHelper.close(additionalApplicationContext);
            IOHelper.close(applicationContext);
        } finally {
            super.doStop();
        }
    }

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        String[] names = getApplicationContext().getBeanNamesForType(ProducerTemplate.class);
        if (names != null && names.length > 0) {
            return getApplicationContext().getBean(names[0], ProducerTemplate.class);
        }
        if (getCamelContext() == null) {
            throw new IllegalArgumentException("No CamelContext are available so cannot create a ProducerTemplate!");
        }
        return getCamelContext().createProducerTemplate();
    }

    protected AbstractApplicationContext createDefaultApplicationContext() {
        ApplicationContext parentContext = getParentApplicationContext();

        // file based
        if (getFileApplicationContextUri() != null) {
            String[] args = getFileApplicationContextUri().split(";");

            if (parentContext != null) {
                return new FileSystemXmlApplicationContext(args, parentContext);
            } else {
                return new FileSystemXmlApplicationContext(args);
            }
        }

        // default to classpath based
        String[] args = getApplicationContextUri().split(";");
        if (parentContext != null) {
            return new ClassPathXmlApplicationContext(args, parentContext);
        } else {
            // okay no application context specified so lets look for either
            // classpath xml or annotation based
            if (mainConfigurationProperties.getRoutesBuilderClasses() != null) {
                AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext();
                ac.register(SpringCamelContext.class);
                Set<String> packages = new LinkedHashSet<>();
                String[] classes = mainConfigurationProperties.getRoutesBuilderClasses().split(",");
                for (String clazz : classes) {
                    if (clazz.contains(".")) {
                        String packageName = clazz.substring(0, clazz.lastIndexOf('.'));
                        packages.add(packageName);
                    }
                }
                LOG.info("Using Spring annotation scanning in packages: {}", packages);
                ac.scan(packages.toArray(new String[0]));
                ac.refresh();
                return ac;
            } else {
                return new ClassPathXmlApplicationContext(args);
            }
        }
    }

    protected AbstractApplicationContext createAdditionalLocationsFromClasspath() throws IOException {
        Set<String> locations = new LinkedHashSet<>();
        findLocations(locations, Main.class.getClassLoader());

        if (!locations.isEmpty()) {
            LOG.info("Found locations for additional Spring XML files: {}", locations);

            String[] locs = locations.toArray(new String[0]);
            return new ClassPathXmlApplicationContext(locs);
        } else {
            return null;
        }
    }

    protected void findLocations(Set<String> locations, ClassLoader classLoader) throws IOException {
        Enumeration<URL> resources = classLoader.getResources(LOCATION_PROPERTIES);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            BufferedReader reader = IOHelper.buffered(new InputStreamReader(url.openStream(), UTF8));
            try {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    locations.add(line);
                }
            } finally {
                IOHelper.close(reader, null, LOG);
            }
        }
    }

}
