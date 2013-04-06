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
package org.apache.camel.spring;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class InjectedBeanTest extends SpringTestSupport {
    protected InjectedBean bean;

    public void testInjectionPoints() throws Exception {
        log.info("getFieldInjectedEndpoint()         = " + bean.getFieldInjectedEndpoint());
        log.info("getPropertyInjectedEndpoint()      = " + bean.getPropertyInjectedEndpoint());

        log.info("getFieldInjectedProducer()         = " + bean.getFieldInjectedProducer());
        log.info("getPropertyInjectedProducer()      = " + bean.getPropertyInjectedProducer());

        log.info("getFieldInjectedCamelTemplate()    = " + bean.getFieldInjectedCamelTemplate());
        log.info("getPropertyInjectedCamelTemplate() = " + bean.getPropertyInjectedCamelTemplate());

        assertEndpointUri(bean.getFieldInjectedEndpoint(), "direct://fieldInjectedEndpoint");
        assertEndpointUri(bean.getPropertyInjectedEndpoint(), "direct://namedEndpoint1");

        assertNotNull("No Producer injected for getFieldInjectedProducer()", bean.getFieldInjectedProducer());
        assertNotNull("No Producer injected for getPropertyInjectedProducer()", bean.getPropertyInjectedProducer());

        assertNotNull("No CamelTemplate injected for getFieldInjectedCamelTemplate()", bean.getFieldInjectedCamelTemplate());
        assertNotNull("No CamelTemplate injected for getPropertyInjectedCamelTemplate()", bean.getPropertyInjectedCamelTemplate());

        assertNotNull("No ProducerTemplate injected for getInjectByFieldName()", bean.getInjectByFieldName());
        assertNotNull("No ProducerTemplate injected for getInjectByPropertyName()", bean.getInjectByPropertyName());

        assertNotNull("No PollingConsumer injected for getFieldInjectedPollingConsumer()", bean.getFieldInjectedPollingConsumer());
        assertNotNull("No PollingConsumer injected for getPropertyInjectedPollingConsumer()", bean.getPropertyInjectedPollingConsumer());
    }

    public void testSendAndReceive() throws Exception {
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        bean = getMandatoryBean(InjectedBean.class, "injectedBean");
    }

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/injectedBean.xml");
    }

}
