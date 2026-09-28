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
package org.apache.camel.spring.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.CamelContextCustomizer;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.support.jsse.GlobalSSLContextParametersSupplier;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SpringCamelContextConfigurationTest extends SpringTestSupport {

    private static final SSLContextParameters SSL = new SSLContextParameters();

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/impl/SpringCamelContextConfigurationTest.xml");
    }

    @Test
    public void testStreamCaching() {
        // streamCaching enabled=false turns off stream caching
        assertFalse(context.isStreamCaching());
        assertFalse(context.getStreamCachingStrategy().isEnabled());
        assertEquals(4096, context.getStreamCachingStrategy().getBufferSize());
    }

    @Test
    public void testRouteControllerNotSupervising() {
        assertFalse(context.getRouteController() instanceof SupervisingRouteController);
    }

    @Test
    public void testCustomizerAndSSLContextParameters() {
        assertEquals("true", context.getGlobalOption("customized"));
        assertSame(SSL, context.getSSLContextParameters());
    }

    public static class MyCustomizer implements CamelContextCustomizer {

        @Override
        public void configure(CamelContext camelContext) {
            camelContext.getGlobalOptions().put("customized", "true");
        }
    }

    public static class MySSLSupplier implements GlobalSSLContextParametersSupplier {

        @Override
        public SSLContextParameters get() {
            return SSL;
        }
    }
}
