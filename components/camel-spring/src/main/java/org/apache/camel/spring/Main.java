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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import javax.xml.bind.JAXBException;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spring.handler.CamelNamespaceHandler;
import org.apache.camel.util.MainSupport;
import org.apache.camel.view.ModelFileGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * A command line tool for booting up a CamelContext using an optional Spring
 * ApplicationContext
 *
 * @version $Revision$
 */
public class Main extends MainSupport {
    private static Main instance;

    private String applicationContextUri = "META-INF/spring/*.xml";
    private String fileApplicationContextUri;
    private AbstractApplicationContext applicationContext;
    private AbstractApplicationContext parentApplicationContext;
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
    
    /**
     * A class for intercepting the hang up signal and do a graceful shutdown of
     * the Camel.
     */
    private class HangupInterceptor extends Thread {
        Log log = LogFactory.getLog(this.getClass());
        Main mainInstance;

        public HangupInterceptor(Main main) {
            mainInstance = main;
        }

        @Override
        public void run() {
            log.info("Recieved hang up - stopping the main instance.");
            try {
                mainInstance.stop();
            } catch (Exception ex) {
                log.warn(ex);
            }
        }
    }


    public static void main(String... args) {
        Main main = new Main();
        instance = main;
        main.enableHangupSupport();
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
    
    /**
     * Enables the hangup support. Gracefully stops by calling stop() on a
     * Hangup signal.
     */
    public void enableHangupSupport() {
        HangupInterceptor interceptor = new HangupInterceptor(this);
        Runtime.getRuntime().addShutdownHook(interceptor);
    }

    @Override
    public void enableDebug() {
        super.enableDebug();
        setParentApplicationContextUri("/META-INF/services/org/apache/camel/spring/debug.xml");
    }

    @Override
    public void enableTrace() {
        super.enableTrace();
        setParentApplicationContextUri("/META-INF/services/org/apache/camel/spring/trace.xml");
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
        super.doStart();
        if (applicationContext == null) {
            applicationContext = createDefaultApplicationContext();
        }
        LOG.debug("Starting Spring ApplicationContext: " + applicationContext.getId());
        applicationContext.start();

        postProcessContext();
    }

    protected void doStop() throws Exception {
        super.doStop();
        if (applicationContext != null) {
            LOG.debug("Stopping Spring ApplicationContext: " + applicationContext.getId());
            applicationContext.close();
        }
    }

    protected ProducerTemplate findOrCreateCamelTemplate() {
        String[] names = getApplicationContext().getBeanNamesForType(ProducerTemplate.class);
        if (names != null && names.length > 0) {
            return (ProducerTemplate) getApplicationContext().getBean(names[0], ProducerTemplate.class);
        }
        for (CamelContext camelContext : getCamelContexts()) {
            return camelContext.createProducerTemplate();
        }
        throw new IllegalArgumentException("No CamelContexts are available so cannot create a ProducerTemplate!");
    }

    protected AbstractApplicationContext createDefaultApplicationContext() {
        // file based
        if (getFileApplicationContextUri() != null) {
            String[] args = getFileApplicationContextUri().split(";");

            ApplicationContext parentContext = getParentApplicationContext();
            if (parentContext != null) {
                return new FileSystemXmlApplicationContext(args, parentContext);
            } else {
                return new FileSystemXmlApplicationContext(args);
            }
        }

        // default to classpath based
        String[] args = getApplicationContextUri().split(";");
        ApplicationContext parentContext = getParentApplicationContext();
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

    protected ModelFileGenerator createModelFileGenerator() throws JAXBException {
        return new ModelFileGenerator(new CamelNamespaceHandler().getJaxbContext());
    }
}
