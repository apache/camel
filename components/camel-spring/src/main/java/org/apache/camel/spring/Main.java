/**
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.MainSupport;
import org.apache.camel.util.IOHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * A command line tool for booting up a CamelContext using an optional Spring
 * {@link org.springframework.context.ApplicationContext}.
 * <p/>
 * By placing a file in the {@link #LOCATION_PROPERTIES} directory of any JARs on the classpath,
 * allows this Main class to load those additional Spring XML files as Spring
 * {@link org.springframework.context.ApplicationContext} to be included.
 * <p/>
 * Each line in the {@link #LOCATION_PROPERTIES} is a reference to a Spring XML file to include,
 * which by default gets loaded from classpath.
 */
@SuppressWarnings("deprecation")
public class Main extends MainSupport {

    public static final String LOCATION_PROPERTIES = "META-INF/camel-spring/location.properties";
    protected static Main instance;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private String applicationContextUri = "META-INF/spring/*.xml";
    private String fileApplicationContextUri;
    private AbstractApplicationContext applicationContext;
    private AbstractApplicationContext parentApplicationContext;
    private AbstractApplicationContext additionalApplicationContext;
    private String parentApplicationContextUri;

    public Main() {

        addOption(new ParameterOption("ac", "applicationContext",
                "Sets the classpath based spring ApplicationContext", "applicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setApplicationContextUri(parameter);
            }
        });

        addOption(new ParameterOption("fa", "fileApplicationContext",
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

    // Implementation methods
    // -------------------------------------------------------------------------

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
                    LOG.debug("Starting Additional ApplicationContext: " + additionalApplicationContext.getId());
                    additionalApplicationContext.start();
                }
            }

            LOG.debug("Starting Spring ApplicationContext: " + applicationContext.getId());
            applicationContext.start();

            postProcessContext();
        } finally {
            if (camelContexts != null && !camelContexts.isEmpty()) {
                // if we were veto started then mark as completed
                if (getCamelContexts().get(0).isVetoStarted()) {
                    completed();
                }
            }
        }
    }

    protected void doStop() throws Exception {
        super.doStop();
        if (additionalApplicationContext != null) {
            LOG.debug("Stopping Additional ApplicationContext: " + additionalApplicationContext.getId());
            IOHelper.close(additionalApplicationContext);
        }
        if (applicationContext != null) {
            LOG.debug("Stopping Spring ApplicationContext: " + applicationContext.getId());
            IOHelper.close(applicationContext);
        }
    }

    protected ProducerTemplate findOrCreateCamelTemplate() {
        String[] names = getApplicationContext().getBeanNamesForType(ProducerTemplate.class);
        if (names != null && names.length > 0) {
            return getApplicationContext().getBean(names[0], ProducerTemplate.class);
        }
        if (getCamelContexts().isEmpty()) {
            throw new IllegalArgumentException("No CamelContexts are available so cannot create a ProducerTemplate!");
        }
        return getCamelContexts().get(0).createProducerTemplate();
    }

    protected AbstractApplicationContext createDefaultApplicationContext() throws IOException {
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
            return new ClassPathXmlApplicationContext(args);
        }
    }

    protected Map<String, CamelContext> getCamelContextMap() {
        Map<String, SpringCamelContext> map = applicationContext.getBeansOfType(SpringCamelContext.class);
        Set<Map.Entry<String, SpringCamelContext>> entries = map.entrySet();
        Map<String, CamelContext> answer = new HashMap<String, CamelContext>();
        for (Map.Entry<String, SpringCamelContext> entry : entries) {
            String name = entry.getKey();
            CamelContext camelContext = entry.getValue();
            answer.put(name, camelContext);
        }
        return answer;
    }

    protected AbstractApplicationContext createAdditionalLocationsFromClasspath() throws IOException {
        Set<String> locations = new LinkedHashSet<String>();
        findLocations(locations, Main.class.getClassLoader());

        if (!locations.isEmpty()) {
            LOG.info("Found locations for additional Spring XML files: {}", locations);

            String[] locs = locations.toArray(new String[locations.size()]);
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
                    if (line.startsWith("#") || line.length() == 0) {
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
