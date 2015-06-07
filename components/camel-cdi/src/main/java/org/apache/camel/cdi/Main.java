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
package org.apache.camel.cdi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBContext;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultModelJAXBContextFactory;
import org.apache.camel.main.MainSupport;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.deltaspike.core.api.provider.BeanProvider;

/**
 * Allows Camel and CDI applications to be booted up on the command line as a Java Application
 */
public abstract class Main extends MainSupport { // abstract to prevent cdi management
    private static Main instance;
    private JAXBContext jaxbContext;
    private Object cdiContainer; // we don't want to need cdictrl API in OSGi

    public Main() {
        // add options...
    }

    public static void main(String... args) throws Exception {
        Main main = new Main() { };
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

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        ProducerTemplate answer = BeanProvider.getContextualReference(ProducerTemplate.class, true);
        if (answer != null) {
            return answer;
        }
        if (getCamelContexts().isEmpty()) {
            throw new IllegalArgumentException(
                    "No CamelContexts are available so cannot create a ProducerTemplate!");
        }
        return getCamelContexts().get(0).createProducerTemplate();
    }

    @Override
    protected Map<String, CamelContext> getCamelContextMap() {
        List<CamelContext> contexts = BeanProvider.getContextualReferences(CamelContext.class, true);
        Map<String, CamelContext> answer = new HashMap<String, CamelContext>();
        for (CamelContext context : contexts) {
            String name = context.getName();
            answer.put(name, context);
        }
        return answer;
    }

    public ModelJAXBContextFactory getModelJAXBContextFactory() {
        return new DefaultModelJAXBContextFactory();
    }

    @Override
    protected void doStart() throws Exception {
        org.apache.deltaspike.cdise.api.CdiContainer container = org.apache.deltaspike.cdise.api.CdiContainerLoader.getCdiContainer();
        container.boot();
        container.getContextControl().startContexts();
        cdiContainer = container;
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (cdiContainer != null) {
            ((org.apache.deltaspike.cdise.api.CdiContainer) cdiContainer).shutdown();
        }
    }
}
