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
package org.apache.camel.spring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class InjectedBeanTest extends SpringTestSupport {
    protected InjectedBean bean;

    @Test
    public void testInjectionPoints() throws Exception {
        log.info("getFieldInjectedEndpoint()         = {}", bean.getFieldInjectedEndpoint());
        log.info("getPropertyInjectedEndpoint()      = {}", bean.getPropertyInjectedEndpoint());

        log.info("getFieldInjectedProducer()         = {}", bean.getFieldInjectedProducer());
        log.info("getPropertyInjectedProducer()      = {}", bean.getPropertyInjectedProducer());

        log.info("getFieldInjectedCamelTemplate()    = {}", bean.getFieldInjectedCamelTemplate());
        log.info("getPropertyInjectedCamelTemplate() = {}", bean.getPropertyInjectedCamelTemplate());

        assertEndpointUri(bean.getFieldInjectedEndpoint(), "direct://fieldInjectedEndpoint");
        assertEndpointUri(bean.getPropertyInjectedEndpoint(), "direct://namedEndpoint1");

        assertNotNull(bean.getFieldInjectedProducer(), "No Producer injected for getFieldInjectedProducer()");
        assertNotNull(bean.getPropertyInjectedProducer(), "No Producer injected for getPropertyInjectedProducer()");

        assertNotNull(bean.getFieldInjectedCamelTemplate(), "No CamelTemplate injected for getFieldInjectedCamelTemplate()");
        assertNotNull(bean.getPropertyInjectedCamelTemplate(),
                "No CamelTemplate injected for getPropertyInjectedCamelTemplate()");

        assertNotNull(bean.getInjectByFieldName(), "No ProducerTemplate injected for getInjectByFieldName()");
        assertNotNull(bean.getInjectByPropertyName(), "No ProducerTemplate injected for getInjectByPropertyName()");

        assertNotNull(bean.getFieldInjectedPollingConsumer(),
                "No PollingConsumer injected for getFieldInjectedPollingConsumer()");
        assertNotNull(bean.getPropertyInjectedPollingConsumer(),
                "No PollingConsumer injected for getPropertyInjectedPollingConsumer()");
    }

    @Test
    public void testSendAndReceive() throws Exception {
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        bean = getMandatoryBean(InjectedBean.class, "injectedBean");
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/injectedBean.xml");
    }

}
