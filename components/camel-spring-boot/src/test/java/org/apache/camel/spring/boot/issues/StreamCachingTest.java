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
package org.apache.camel.spring.boot.issues;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static java.util.Arrays.asList;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.boot.SpringTypeConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;

public class StreamCachingTest extends CamelTestSupport {

    public static final String URI_END_OF_ROUTE = "mock:end_of_route";

    @EndpointInject(uri = URI_END_OF_ROUTE)
    private MockEndpoint endOfRoute;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getTypeConverterRegistry().addFallbackTypeConverter(springTypeConverter(context, new ConversionService[]{new DefaultConversionService()}), true);

                from("direct:foo")
                    .streamCaching()
                    .bean(MyBean.class)
                    .to(URI_END_OF_ROUTE);
            }
        };
    }

    @Test
    public void streamCachingWithSpring() throws Exception {
        endOfRoute.expectedMessageCount(1);

        template.sendBody("direct:foo", new FileInputStream(new File("src/test/resources/logback.xml")));

        endOfRoute.assertIsSatisfied();
    }

    public static class MyBean {
        public List<Integer> someNumbers() {
            return asList(1, 2, 3);
        }
    }

    /**
     * Copied from org.apache.camel.spring.boot.TypeConversionConfiguration (they are package protected)
     **/
    @Bean
    SpringTypeConverter springTypeConverter(CamelContext camelContext, ConversionService[] conversionServices) {
        SpringTypeConverter springTypeConverter = new SpringTypeConverter(asList(conversionServices));
        camelContext.getTypeConverterRegistry().addFallbackTypeConverter(springTypeConverter, true);
        return springTypeConverter;
    }

    @ConditionalOnMissingBean
    @Bean
    ConversionService defaultCamelConversionService(ApplicationContext applicationContext) {
        DefaultConversionService service = new DefaultConversionService();
        for (Converter converter : applicationContext.getBeansOfType(Converter.class).values()) {
            service.addConverter(converter);
        }
        return service;
    }

}