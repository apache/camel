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
package org.apache.camel.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.apache.camel.Consume;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class CamelPostProcessorHelperTest extends ContextTestSupport {

    private MySynchronization mySynchronization = new MySynchronization();

    public void testConstructor() {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper();
        assertNull(helper.getCamelContext());

        helper.setCamelContext(context);
        assertNotNull(helper.getCamelContext());
    }

    public void testConstructorCamelContext() {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);
        assertNotNull(helper.getCamelContext());
    }

    public void testMatchContext() {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);
        assertTrue(helper.matchContext(context.getName()));
        assertFalse(helper.matchContext("foo"));
    }

    public void testConsume() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeBean my = new MyConsumeBean();
        Method method = my.getClass().getMethod("consumeSomething", String.class);
        helper.consumerInjection(method, my, "foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testConsumeSynchronization() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyConsumeAndSynchronizationBean my = new MyConsumeAndSynchronizationBean();
        Method method = my.getClass().getMethod("consumeSomething", String.class, Exchange.class);
        helper.consumerInjection(method, my, "foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

        // give UoW a bit of time
        Thread.sleep(500);

        assertTrue("Should have invoked onDone", mySynchronization.isOnDone());
    }

    public void testProduceSynchronization() throws Exception {
        MyProduceAndSynchronizationBean my = new MyProduceAndSynchronizationBean();

        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);
        Producer producer = helper.createInjectionProducer(context.getEndpoint("mock:result"), my, "foo");
        my.setProducer(producer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        my.produceSomething("Hello World");

        assertMockEndpointsSatisfied();

        // give UoW a bit of time
        Thread.sleep(500);

        assertTrue("Should have invoked onDone", mySynchronization.isOnDone());
    }

    public void testEndpointInjectProducerTemplate() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointInjectBeanProducerTemplate bean = new MyEndpointInjectBeanProducerTemplate();
        Method method = bean.getClass().getMethod("setProducer", ProducerTemplate.class);

        EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class type : parameterTypes) {
            String propertyName = ObjectHelper.getPropertyName(method);
            Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");
            ObjectHelper.invokeMethod(method, bean, value);
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        assertNotNull(bean.getProducer());
        bean.send("Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testEndpointInjectProducer() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointBeanProducer bean = new MyEndpointBeanProducer();
        Method method = bean.getClass().getMethod("setProducer", Producer.class);

        EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class type : parameterTypes) {
            String propertyName = ObjectHelper.getPropertyName(method);
            Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");
            ObjectHelper.invokeMethod(method, bean, value);
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        assertNotNull(bean.getProducer());

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");

        bean.send(exchange);

        assertMockEndpointsSatisfied();
    }

    public void testEndpointInjectPollingConsumer() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointBeanPollingConsumer bean = new MyEndpointBeanPollingConsumer();
        Method method = bean.getClass().getMethod("setConsumer", PollingConsumer.class);

        EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class type : parameterTypes) {
            String propertyName = ObjectHelper.getPropertyName(method);
            Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");
            ObjectHelper.invokeMethod(method, bean, value);
        }

        template.sendBody("seda:foo", "Hello World");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        assertNotNull(bean.getConsumer());

        Exchange exchange = bean.consume();
        template.send("mock:result", exchange);

        assertMockEndpointsSatisfied();
    }

    public void testEndpointInjectProducerTemplateField() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointInjectProducerTemplate bean = new MyEndpointInjectProducerTemplate();
        Field field = bean.getClass().getField("producer");

        EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
        Class<?> type = field.getType();
        String propertyName = "producer";
        Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");

        field.set(bean, value);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");

        bean.send(exchange);

        assertMockEndpointsSatisfied();
    }

    public void testEndpointInjectProducerTemplateFieldNoDefaultEndpoint() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointInjectProducerTemplateNoDefaultEndpoint bean = new MyEndpointInjectProducerTemplateNoDefaultEndpoint();
        Field field = bean.getClass().getField("producer");

        EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
        Class<?> type = field.getType();
        String propertyName = "producer";
        Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");

        field.set(bean, value);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");

        bean.send(exchange);

        assertMockEndpointsSatisfied();
    }

    public void testEndpointInjectProducerTemplateFieldNameUnknown() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointInjectProducerTemplateNameUnknown bean = new MyEndpointInjectProducerTemplateNameUnknown();
        Field field = bean.getClass().getField("producer");

        EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
        Class<?> type = field.getType();
        String propertyName = "producer";

        try {
            helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals("registry entry called unknown of type org.apache.camel.Endpoint must be specified", e.getMessage());
        }
    }

    public void testEndpointInjectProducerTemplateFieldUrlUnknown() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointInjectProducerTemplateUrlUnknown bean = new MyEndpointInjectProducerTemplateUrlUnknown();
        Field field = bean.getClass().getField("producer");

        EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
        Class<?> type = field.getType();
        String propertyName = "producer";

        try {
            helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");
            fail("Should throw exception");
        } catch (ResolveEndpointFailedException e) {
            assertEquals("Failed to resolve endpoint: xxx://foo due to: No component found with scheme: xxx", e.getMessage());
        }
    }

    public void testEndpointInjectBothUriAndRef() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointBothUriAndRef bean = new MyEndpointBothUriAndRef();
        Field field = bean.getClass().getField("producer");

        EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
        Class<?> type = field.getType();
        String propertyName = "producer";

        try {
            helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), propertyName, bean, "foo");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Both uri and name is provided, only either one is allowed: uri=seda:foo, ref=myEndpoint", e.getMessage());
        }
    }

    public class MyConsumeBean {

        @Consume(uri = "seda:foo")
        public void consumeSomething(String body) {
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }
    }

    public class MyConsumeAndSynchronizationBean {

        @Consume(uri = "seda:foo")
        public void consumeSomething(String body, Exchange exchange) {
            exchange.addOnCompletion(mySynchronization);
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }
    }

    public class MyProduceAndSynchronizationBean {

        @Produce(uri = "mock:result")
        Producer producer;

        public void produceSomething(String body) throws Exception {
            assertEquals("Hello World", body);

            Exchange exchange = producer.createExchange();
            exchange.addOnCompletion(mySynchronization);
            exchange.getIn().setBody(body);
            producer.process(exchange);
        }

        public void setProducer(Producer producer) {
            this.producer = producer;
        }
    }

    private class MySynchronization extends SynchronizationAdapter {

        private boolean onDone;

        @Override
        public void onDone(Exchange exchange) {
            onDone = true;
        }

        public boolean isOnDone() {
            return onDone;
        }
    }

    public class MyEndpointInjectBeanProducerTemplate {

        private ProducerTemplate producer;

        @EndpointInject(uri = "mock:result")
        public void setProducer(ProducerTemplate producer) {
            this.producer = producer;
        }

        public ProducerTemplate getProducer() {
            return producer;
        }

        public void send(String message) {
            producer.sendBody(message);
        }
    }

    public class MyEndpointBeanProducer {

        private Producer producer;

        @EndpointInject(uri = "mock:result")
        public void setProducer(Producer producer) {
            this.producer = producer;
        }

        public Producer getProducer() {
            return producer;
        }

        public void send(Exchange exchange) throws Exception {
            producer.process(exchange);
        }

    }

    public class MyEndpointBeanPollingConsumer {

        private PollingConsumer consumer;

        @EndpointInject(uri = "seda:foo")
        public void setConsumer(PollingConsumer consumer) {
            this.consumer = consumer;
        }

        public PollingConsumer getConsumer() {
            return consumer;
        }

        public Exchange consume() throws Exception {
            return consumer.receive(1000);
        }

    }

    public class MyEndpointInjectProducerTemplate {

        @EndpointInject(uri = "mock:result")
        public ProducerTemplate producer;

        public void send(Exchange exchange) throws Exception {
            producer.send(exchange);
        }

    }

    public class MyEndpointInjectProducerTemplateNoDefaultEndpoint {

        @EndpointInject()
        public ProducerTemplate producer;

        public void send(Exchange exchange) throws Exception {
            producer.send("mock:result", exchange);
        }

    }

    public class MyEndpointInjectProducerTemplateNameUnknown {

        @EndpointInject(ref = "unknown")
        public ProducerTemplate producer;

        public void send(Exchange exchange) throws Exception {
            producer.send(exchange);
        }

    }

    public class MyEndpointInjectProducerTemplateUrlUnknown {

        @EndpointInject(uri = "xxx:foo")
        public ProducerTemplate producer;

        public void send(Exchange exchange) throws Exception {
            producer.send(exchange);
        }

    }

    public class MyEndpointBothUriAndRef {

        @EndpointInject(uri = "seda:foo", ref = "myEndpoint")
        public ProducerTemplate producer;

        public void send(Exchange exchange) throws Exception {
            producer.send(exchange);
        }

    }

}
