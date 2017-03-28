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
package org.apache.camel.example.cdi.cassandraql;

import java.util.Arrays;
import java.util.Properties;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.component.properties.DefaultPropertiesParser;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.properties.PropertiesParser;
import org.apache.deltaspike.core.api.config.ConfigResolver;

/**
 * Example application
 */
public class Application {

    @ContextName("camel-example-cassandraql-cdi")
    static class KubernetesRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("timer:stream?repeatCount=1")
                .to("cql://{{cassandra-master-ip}},{{cassandra-node1-ip}},{{cassandra-node2-ip}}/test?cql={{cql-select-query}}&consistencyLevel=quorum")
                .log("Result from query ${body}")
                .process(exchange -> {
                    exchange.getIn().setBody(Arrays.asList("davsclaus"));
                })
                .to("cql://{{cassandra-master-ip}},{{cassandra-node1-ip}},{{cassandra-node2-ip}}/test?cql={{cql-insert-query}}&consistencyLevel=quorum");
        }
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
        public String parseProperty(String key, String value, Properties properties) {
            return ConfigResolver.getPropertyValue(key);
        }
    }
}
