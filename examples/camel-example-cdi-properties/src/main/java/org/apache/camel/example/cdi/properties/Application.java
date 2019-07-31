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
package org.apache.camel.example.cdi.properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesLookup;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.apache.deltaspike.core.api.config.ConfigResolver;

public class Application {

    @ApplicationScoped
    static class HelloRoute extends RouteBuilder {

        @Override
        public void configure() {
            // Property placeholders in endpoint URIs are resolved
            // based on configuration properties
            from("{{destination}}").log("${body} from CamelContext (${camelContext.name})");
        }
    }

    void hello(@Observes CamelContextStartedEvent event,
               // Configuration properties can be injected with @ConfigProperty
               @ConfigProperty(name = "message") String message,
               // Property placeholders in @Uri qualifier are also resolved
               @Uri("{{destination}}") ProducerTemplate producer) {
        producer.sendBody(message);
    }

    @Produces
    @ApplicationScoped
    @Named("properties")
    // "properties" component bean that Camel uses to lookup properties
    PropertiesComponent properties(PropertiesParser parser) {
        PropertiesComponent component = new PropertiesComponent();
        // Use DeltaSpike as configuration source for Camel CDI
        component.setPropertiesParser(parser);
        return component;
    }

    // PropertiesParser bean that uses DeltaSpike to resolve properties
    static class DeltaSpikeParser extends DefaultPropertiesParser {

        @Override
        public String parseProperty(String key, String value, PropertiesLookup properties) {
            return ConfigResolver.getPropertyValue(key);
        }
    }
}
