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
package org.apache.camel.spring.boot.parent;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.RoutesCollector;
import org.junit.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

public class SpringBootRefreshContextTest {

    @Test
    public void shouldOnlyCollectRoutesOnce() {
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.refresh();
        ConfigurableApplicationContext context = new SpringApplicationBuilder(Configuration.class).web(false).parent(parent).run();
        ContextRefreshedEvent refreshEvent = new ContextRefreshedEvent(context);
        RoutesCollector collector = context.getBean(RoutesCollector.class);
        collector.onApplicationEvent(refreshEvent); //no changes should happen here
    }

}

@SpringBootApplication
class Configuration {

    @Bean
    RoutesBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:test").to("mock:test");
            }
        };
    }

}