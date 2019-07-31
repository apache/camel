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
package org.apache.camel.component.cm.test;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Builds a SimpleRoute to send a message to CM GW and CM Uri is built based on
 * properties in a file.
 */
@Configuration("cmConfig")
@PropertySource("classpath:/cm-smsgw.properties")
public class CamelTestConfiguration extends SingleRouteCamelConfiguration {

    public static final String SIMPLE_ROUTE_ID = "simple-route";

    private String uri;

    @Override
    public RouteBuilder route() {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {

                Assert.hasLength(uri);

                log.debug("CM Component is an URI based component\nCM URI: {}", uri);

                // Route definition
                from("direct:sms").to(uri).to("mock:test")
                        .routeId(SIMPLE_ROUTE_ID);

            }
        };
    }

    @Bean
    public LocalValidatorFactoryBean getValidatorFactory() {
        final LocalValidatorFactoryBean localValidatorFactoryBean = new LocalValidatorFactoryBean();
        localValidatorFactoryBean.getValidationPropertyMap()
                .put("hibernate.validator.fail_fast", "true");
        return localValidatorFactoryBean;
    }

    /**
     * Build the URI of the CM Component based on Environmental properties
     */
    @Override
    public final void setApplicationContext(
            final ApplicationContext applicationContext) {

        super.setApplicationContext(applicationContext);

        final Environment env = applicationContext.getEnvironment();

        final String host = env.getRequiredProperty("cm.url");
        final String productTokenString = env
                .getRequiredProperty("cm.product-token");
        final String sender = env.getRequiredProperty("cm.default-sender");

        final StringBuffer cmUri = new StringBuffer("cm-sms:" + host)
                .append("?productToken=").append(productTokenString);
        if (sender != null && !sender.isEmpty()) {
            cmUri.append("&defaultFrom=").append(sender);
        }

        // Defaults to false
        final Boolean testConnectionOnStartup = Boolean.parseBoolean(
                env.getProperty("cm.testConnectionOnStartup", "false"));
        if (testConnectionOnStartup) {
            cmUri.append("&testConnectionOnStartup=")
                    .append(testConnectionOnStartup.toString());
        }

        // Defaults to 8
        final Integer defaultMaxNumberOfParts = Integer
                .parseInt(env.getProperty("defaultMaxNumberOfParts", "8"));
        cmUri.append("&defaultMaxNumberOfParts=")
                .append(defaultMaxNumberOfParts.toString());

        uri = cmUri.toString();
    }

    public String getUri() {
        return uri;
    }
}
