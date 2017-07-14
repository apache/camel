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

import java.io.BufferedInputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.naming.Context;
import javax.naming.InitialContext;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.guice.inject.Injectors;
import org.apache.camel.main.MainSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A command line tool for booting up a CamelContext using a Guice Injector via JNDI
 * assuming that a valid jndi.properties is on the classpath
 *
 * @version 
 */
public class Main extends MainSupport {
    private static Main instance;
    private Injector injector;
    private String jndiProperties;
 
    public Main() {
        addOption(new ParameterOption("j", "jndiProperties",
                "Sets the classpath based jndi properties file location", "jndiProperties") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setJndiProperties(parameter);
                
            }
        });
    }
    
    public void setJndiProperties(String properties) {
        this.jndiProperties = properties;
    }

    public String getJndiProperties() {
        return this.jndiProperties;
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
    protected void setInjector(Injector injector) {
        this.injector = injector;        
    }
    
    protected Injector getInjector() {
        return injector;
    }
    
    // Implementation methods
    // -------------------------------------------------------------------------
    protected Injector getInjectorFromContext() throws Exception {
        Context context = null;
        URL jndiPropertiesUrl = null;
        if (ObjectHelper.isNotEmpty(jndiProperties)) {
            jndiPropertiesUrl = this.getClass().getResource(jndiProperties);
        }
        if (jndiPropertiesUrl != null) {
            Properties properties = new Properties();
            BufferedInputStream bis = null;
            try {
                bis = IOHelper.buffered(jndiPropertiesUrl.openStream());
                properties.load(bis);
            } finally {
                IOHelper.close(bis);
            }
            context = new InitialContext(properties);
        } else {
            context = new InitialContext();
        }
        return (Injector) context.lookup(Injector.class.getName());
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        setInjector(getInjectorFromContext());
        postProcessContext();
    }

    protected void doStop() throws Exception {
        super.doStop();

        if (injector != null) {
            Injectors.close(injector);
        }
    }

    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (injector != null) {
            Set<ProducerTemplate> set = Injectors.getInstancesOf(injector, ProducerTemplate.class);
            if (!set.isEmpty()) {
                // TODO should be Iterables.get(set, 0);
                return Iterables.getOnlyElement(set);
            }
        }
        for (CamelContext camelContext : getCamelContexts()) {
            return camelContext.createProducerTemplate();
        }
        throw new IllegalArgumentException("No CamelContext is available so cannot create a ProducerTemplate!");
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
}