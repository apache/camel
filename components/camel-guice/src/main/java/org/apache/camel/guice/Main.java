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
package org.apache.camel.guice;

import java.util.Map;
import java.util.Set;

import javax.naming.InitialContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.MainSupport;
import org.apache.camel.view.ModelFileGenerator;
import org.guiceyfruit.Injectors;

/**
 * A command line tool for booting up a CamelContext using a Guice Injector via JNDI
 * assuming that a valid jndi.properties is on the classpath
 *
 * @version $Revision$
 */
public class Main extends MainSupport {
    private static Main instance;
    private InitialContext context;
    private Injector injector;


    public Main() {

/*
        addOption(new ParameterOption("a", "applicationContext",
                "Sets the classpath based spring ApplicationContext", "applicationContext") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setApplicationContextUri(parameter);
            }
        });
*/
    }

    public static void main(String... args) {
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


    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        context = new InitialContext();

        injector = (Injector) context.lookup(Injector.class.getName());
        postProcessContext();
    }

    protected void doStop() throws Exception {
        LOG.info("Apache Camel terminating");

        if (injector != null) {
            injector.close();
        }
    }

    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (injector != null) {
            Set<ProducerTemplate> set = Injectors.getInstancesOf(injector, ProducerTemplate.class);
            if (!set.isEmpty()) {
                return Iterables.get(set, 0);
            }
        }
        for (CamelContext camelContext : getCamelContexts()) {
            return camelContext.createProducerTemplate();
        }
        throw new IllegalArgumentException("No CamelContexts are available so cannot create a ProducerTemplate!");
    }

    protected Map<String, CamelContext> getCamelContextMap() {
        Map<String, CamelContext> answer = Maps.newHashMap();
        if (injector != null) {
            Set<Map.Entry<Key<?>, Binding<?>>> entries = injector.getBindings().entrySet();
            for (Map.Entry<Key<?>, Binding<?>> entry : entries) {
                Key<?> key = entry.getKey();
                Class<?> keyType = Injectors.getKeyType(key);
                if (keyType != null && CamelContext.class.isAssignableFrom(keyType)) {
                    Binding<?> binding = entry.getValue();
                    Object value = binding.getProvider().get();
                    if (value != null) {
                        CamelContext castValue = CamelContext.class.cast(value);
                        answer.put(key.toString(), castValue);
                    }
                }
            }
        }
        return answer;
    }

    protected ModelFileGenerator createModelFileGenerator() throws JAXBException {
        return new ModelFileGenerator(
            JAXBContext.newInstance("org.apache.camel.model:org.apache.camel.model.config:org.apache.camel.model.dataformat:org.apache.camel.model.language:org.apache.camel.model.loadbalancer"));
    }
}