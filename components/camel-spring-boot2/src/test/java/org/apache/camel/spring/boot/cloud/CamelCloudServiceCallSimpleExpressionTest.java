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
package org.apache.camel.spring.boot.cloud;

import java.util.Properties;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootApplication
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        CamelCloudServiceCallSimpleExpressionTest.TestConfiguration.class,
        CamelCloudServiceCallSimpleExpressionTest.SpringBootPropertySourceConfig.class
    }
)
public class CamelCloudServiceCallSimpleExpressionTest {
    @Autowired
    private ProducerTemplate template;

    @Test
    public void testServiceCall() throws Exception {
        Assert.assertEquals(String.valueOf(SpringBootPropertyUtil.PORT1), template.requestBody("direct:start", null, String.class));
        Assert.assertEquals(String.valueOf(SpringBootPropertyUtil.PORT3), template.requestBody("direct:start", null, String.class));
    }

    // *************************************
    // Config
    // *************************************

    @Configuration
    public static class TestConfiguration {
        @Bean
        public RouteBuilder myRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        .serviceCall("{{service.name}}");

                    fromF("jetty:http://localhost:%d/hello", SpringBootPropertyUtil.PORT1)
                        .transform()
                        .constant(SpringBootPropertyUtil.PORT1);
                    fromF("jetty:http://localhost:%d/hello", SpringBootPropertyUtil.PORT2)
                        .transform()
                        .constant(SpringBootPropertyUtil.PORT2);
                    fromF("jetty:http://localhost:%d/hello", SpringBootPropertyUtil.PORT3)
                        .transform()
                        .constant(SpringBootPropertyUtil.PORT3);
                }
            };
        }
    }
    
    private static Properties getAllProperties() {
        
        Properties prop = new Properties();
        prop.put("service.name", "custom-svc-list");
        prop.put("camel.cloud.load-balancer.enabled", false);
        prop.put("camel.cloud.service-call.component", "jetty");
        prop.put("camel.cloud.service-call.expression", "${header.CamelServiceCallScheme}:http://${header.CamelServiceCallServiceHost}:${header.CamelServiceCallServicePort}/hello");
        prop.put("camel.cloud.service-call.expression-language", "simple");
        prop.put("camel.cloud.service-discovery.services[custom-svc-list]", SpringBootPropertyUtil.getDiscoveryServices());
        prop.put("camel.cloud.service-filter.blacklist[custom-svc-list]", SpringBootPropertyUtil.getServiceFilterBlacklist());
        prop.put("ribbon.enabled", false);
        prop.put("debug", false);
        return prop;
    }
    
   
   
    // *************************************
    // Config
    // 
    
    @Configuration
    public static class SpringBootPropertySourceConfig {

        @Autowired
        private ConfigurableEnvironment env;

        @Bean
        @Lazy(false)
        public MutablePropertySources springBootPropertySource() {

            MutablePropertySources sources = env.getPropertySources();
            sources.addFirst(new PropertiesPropertySource("boot-test-property", CamelCloudServiceCallSimpleExpressionTest.getAllProperties()));
            return sources;

        }
    }
}

