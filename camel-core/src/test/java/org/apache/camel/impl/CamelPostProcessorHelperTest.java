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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Produce;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyInject;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ObjectHelper;

import static org.awaitility.Awaitility.await;

/**
 * @version
 */
public class CamelPostProcessorHelperTest extends ContextTestSupport {

    private MySynchronization mySynchronization = new MySynchronization();
    private Properties myProp = new Properties();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myProp", myProp);
        jndi.bind("foo", new FooBar());
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        PropertiesComponent pc = context.getComponent("properties", PropertiesComponent.class);
        pc.setLocation("ref:myProp");

        return context;
    }

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

    public void testConsumePrivate() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyPrivateConsumeBean my = new MyPrivateConsumeBean();
        Method method = my.getClass().getDeclaredMethod("consumeSomethingPrivate", String.class);
        try {
            helper.consumerInjection(method, my, "foo");
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue(iae.getMessage().startsWith("The method private void"));
            assertTrue(iae.getMessage().endsWith("(for example the method must be public)"));
        }
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
        await("onDone invokation").atMost(1, TimeUnit.SECONDS).until(mySynchronization::isOnDone);
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
        await("onDone invocation").atMost(1, TimeUnit.SECONDS).until(mySynchronization::isOnDone);
    }

    public void testEndpointInjectProducerTemplate() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointInjectBeanProducerTemplate bean = new MyEndpointInjectBeanProducerTemplate();
        Method method = bean.getClass().getMethod("setProducer", ProducerTemplate.class);

        EndpointInject endpointInject = method.getAnnotation(EndpointInject.class);
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class<?> type : parameterTypes) {
            String propertyName = ObjectHelper.getPropertyName(method);
            Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");
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
        for (Class<?> type : parameterTypes) {
            String propertyName = ObjectHelper.getPropertyName(method);
            Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");
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
        for (Class<?> type : parameterTypes) {
            String propertyName = ObjectHelper.getPropertyName(method);
            Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");
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
        Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");

        field.set(bean, value);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Hello World");

        bean.send(exchange);

        assertMockEndpointsSatisfied();
    }

    public void testEndpointInjectFluentProducerTemplateField() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyEndpointInjectFluentProducerTemplate bean = new MyEndpointInjectFluentProducerTemplate();
        Field field = bean.getClass().getField("producer");

        EndpointInject endpointInject = field.getAnnotation(EndpointInject.class);
        Class<?> type = field.getType();
        String propertyName = "producer";
        Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");

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
        Object value = helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");

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
            helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");
            fail("Should throw exception");
        } catch (NoSuchBeanException e) {
            assertEquals("No bean could be found in the registry for: unknown of type: org.apache.camel.Endpoint", e.getMessage());
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
            helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");
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
            helper.getInjectionValue(type, endpointInject.uri(), endpointInject.ref(), endpointInject.property(), propertyName, bean, "foo");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Both uri and name is provided, only either one is allowed: uri=seda:foo, ref=myEndpoint", e.getMessage());
        }
    }

    public void testPropertyFieldInject() throws Exception {
        myProp.put("myTimeout", "2000");
        myProp.put("myApp", "Camel");

        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyPropertyFieldBean bean = new MyPropertyFieldBean();

        Field field = bean.getClass().getField("timeout");
        PropertyInject propertyInject = field.getAnnotation(PropertyInject.class);
        Class<?> type = field.getType();
        Object value = helper.getInjectionPropertyValue(type, propertyInject.value(), "", "timeout", bean, "foo");
        assertEquals(Integer.valueOf("2000"), Integer.valueOf("" + value));

        field = bean.getClass().getField("greeting");
        propertyInject = field.getAnnotation(PropertyInject.class);
        type = field.getType();
        value = helper.getInjectionPropertyValue(type, propertyInject.value(), "", "greeting", bean, "foo");
        assertEquals("Hello Camel", value);
    }

    public void testPropertyFieldDefaultValueInject() throws Exception {
        myProp.put("myApp", "Camel");

        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyPropertyFieldBean bean = new MyPropertyFieldBean();

        Field field = bean.getClass().getField("timeout");
        PropertyInject propertyInject = field.getAnnotation(PropertyInject.class);
        Class<?> type = field.getType();
        Object value = helper.getInjectionPropertyValue(type, propertyInject.value(), "5000", "timeout", bean, "foo");
        assertEquals(Integer.valueOf("5000"), Integer.valueOf("" + value));

        field = bean.getClass().getField("greeting");
        propertyInject = field.getAnnotation(PropertyInject.class);
        type = field.getType();
        value = helper.getInjectionPropertyValue(type, propertyInject.value(), "", "greeting", bean, "foo");
        assertEquals("Hello Camel", value);
    }

    public void testPropertyMethodInject() throws Exception {
        myProp.put("myTimeout", "2000");
        myProp.put("myApp", "Camel");

        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyPropertyMethodBean bean = new MyPropertyMethodBean();

        Method method = bean.getClass().getMethod("setTimeout", int.class);
        PropertyInject propertyInject = method.getAnnotation(PropertyInject.class);
        Class<?> type = method.getParameterTypes()[0];
        Object value = helper.getInjectionPropertyValue(type, propertyInject.value(), "", "timeout", bean, "foo");
        assertEquals(Integer.valueOf("2000"), Integer.valueOf("" + value));

        method = bean.getClass().getMethod("setGreeting", String.class);
        propertyInject = method.getAnnotation(PropertyInject.class);
        type = method.getParameterTypes()[0];
        value = helper.getInjectionPropertyValue(type, propertyInject.value(), "", "greeting", bean, "foo");
        assertEquals("Hello Camel", value);
    }

    public void testBeanInject() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyBeanInjectBean bean = new MyBeanInjectBean();
        Field field = bean.getClass().getField("foo");

        BeanInject beanInject = field.getAnnotation(BeanInject.class);
        Class<?> type = field.getType();
        Object value = helper.getInjectionBeanValue(type, beanInject.value());
        field.set(bean, value);

        String out = bean.doSomething("World");
        assertEquals("Hello World", out);
    }

    public void testBeanInjectNotFound() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyBeanInjectBean bean = new MyBeanInjectBean();
        Field field = bean.getClass().getField("foo");

        Class<?> type = field.getType();
        try {
            helper.getInjectionBeanValue(type, "bar");
            fail("Should have thrown exception");
        } catch (NoSuchBeanException e) {
            assertEquals("No bean could be found in the registry for: bar of type: org.apache.camel.impl.FooBar", e.getMessage());
            assertEquals("bar", e.getName());
        }
    }

    public void testBeanInjectByType() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);

        MyBeanInjectByTypeBean bean = new MyBeanInjectByTypeBean();
        Field field = bean.getClass().getField("foo");

        BeanInject beanInject = field.getAnnotation(BeanInject.class);
        Class<?> type = field.getType();
        Object value = helper.getInjectionBeanValue(type, beanInject.value());
        field.set(bean, value);

        String out = bean.doSomething("Camel");
        assertEquals("Hello Camel", out);
    }

    public void testFluentProducerTemplateWithNoInjection() throws Exception {
        CamelPostProcessorHelper helper = new CamelPostProcessorHelper(context);
        NoBeanInjectionTestClass myBean = new NoBeanInjectionTestClass();
        Field field = myBean.getClass().getField("fluentProducerTemplate");
        EndpointInject inject = field.getAnnotation(EndpointInject.class);
        String propertyName = "fluent";
        Class<?> classType = field.getType();
        Object value = helper.getInjectionValue(classType, inject.uri(), inject.ref(), inject.property(), propertyName, myBean, "bla");

        field.set(myBean, value);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bla Bla Bla. .");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("Bla Bla Bla. .");

        myBean.sendExchange(exchange);

        assertMockEndpointsSatisfied();

    }

    public class NoBeanInjectionTestClass {
        @EndpointInject
        public FluentProducerTemplate fluentProducerTemplate;

        public void sendExchange(Exchange exchange) {
            fluentProducerTemplate.withExchange(exchange).to("mock:result").send();
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

            Exchange exchange = producer.getEndpoint().createExchange();
            exchange.addOnCompletion(mySynchronization);
            exchange.getIn().setBody(body);
            producer.process(exchange);
        }

        public void setProducer(Producer producer) {
            this.producer = producer;
        }
    }

    private static class MySynchronization extends SynchronizationAdapter {

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

    public class MyEndpointInjectFluentProducerTemplate {

        @EndpointInject(uri = "mock:result")
        public FluentProducerTemplate producer;

        public void send(Exchange exchange) throws Exception {
            producer.withExchange(exchange).send();
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

    public class MyPrivateConsumeBean {

        @Consume(uri = "seda:foo")
        private void consumeSomethingPrivate(String body) {
            assertEquals("Hello World", body);
            template.sendBody("mock:result", body);
        }
    }

    public class MyPropertyFieldBean {

        @PropertyInject("myTimeout")
        public int timeout;

        @PropertyInject("Hello {{myApp}}")
        public String greeting;

        public String doSomething(String body) {
            return greeting + " " + body + " with timeout=" + timeout;
        }
    }

    public class MyPropertyFieldDefaultValueBean {

        @PropertyInject(value = "myTimeout", defaultValue = "5000")
        public int timeout;

        @PropertyInject("Hello {{myApp}}")
        public String greeting;

        public String doSomething(String body) {
            return greeting + " " + body + " with timeout=" + timeout;
        }
    }

    public class MyPropertyMethodBean {

        private int timeout;
        private String greeting;

        public String doSomething(String body) {
            return greeting + " " + body + " with timeout=" + timeout;
        }

        public int getTimeout() {
            return timeout;
        }

        @PropertyInject("myTimeout")
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public String getGreeting() {
            return greeting;
        }

        @PropertyInject("Hello {{myApp}}")
        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }
    }

    public class MyBeanInjectBean {

        @BeanInject("foo")
        public FooBar foo;

        public String doSomething(String body) {
            return foo.hello(body);
        }
    }

    public class MyBeanInjectByTypeBean {

        @BeanInject
        public FooBar foo;

        public String doSomething(String body) {
            return foo.hello(body);
        }
    }

}
