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
package org.apache.camel.component.jmx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jmx.beans.ISimpleMXBean;
import org.apache.camel.component.jmx.beans.SimpleBean;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.junit.After;
import org.junit.Before;
import org.w3c.dom.Document;

/**
 * MBean that is registered for the unit tests. The fixture will register a bean
 * and provide access to the mxbean so tests can invoke methods on the mxbean
 * to trigger notifications.
 */
public class SimpleBeanFixture {

    /**
     * domain to use for the mbean
     */
    protected static final String DOMAIN = "TestDomain";
    /**
     * key for the object name
     */
    protected static final String NAME = "name";

    protected MBeanServer server;

    /**
     * camel context to stand up for the test
     */
    private DefaultCamelContext mContext = new DefaultCamelContext();
    /**
     * registry to store referenced beans (i.e. objectProperties or NotificationFilter)
     */
    private SimpleRegistry mRegistry = new SimpleRegistry();
    /**
     * destination for the simple route created.
     */
    private MockEndpoint mMockEndpoint;


    @Before
    public void setUp() throws Exception {
        initServer();
        initBean();
        initRegistry();
        initContext();
        startContext();
    }

    protected void startContext() throws Exception {
        mContext.start();
    }

    @After
    public void tearDown() throws Exception {
        if (!mContext.isStopped()) {
            mContext.stop();
        }
        unregisterBean(makeObjectName("simpleBean"));
    }

    protected void waitForMessages() throws InterruptedException {
        waitForMessages(mMockEndpoint);
    }

    protected void waitForMessages(MockEndpoint aMockEndpoint) throws InterruptedException {
        mMockEndpoint.await(10, TimeUnit.SECONDS);
        assertEquals("Expected number of messages didn't arrive before timeout", aMockEndpoint.getExpectedCount(), aMockEndpoint.getReceivedCounter());
    }

    protected void initServer() throws Exception {
        server = ManagementFactory.getPlatformMBeanServer();
    }

    /**
     * Registers the bean on the platform mbean server
     */
    protected void registerBean(Object aBean, ObjectName aObjectName) throws Exception {
        server.registerMBean(aBean, aObjectName);
    }

    /**
     * Unregisters the bean
     */
    protected void unregisterBean(ObjectName aObjectName) throws MBeanRegistrationException, InstanceNotFoundException {
        server.unregisterMBean(aObjectName);
    }

    /**
     * Gets the mxbean for our remote object using the specified name
     */
    protected ISimpleMXBean getMXBean(ObjectName aObjectName) {
        return (ISimpleMXBean) MBeanServerInvocationHandler.newProxyInstance(
                server,
                aObjectName,
                ISimpleMXBean.class,
                false);
        // revert the above change to the below when we move to JDK 1.6
//        ISimpleMXBean simpleBean = JMX.newMXBeanProxy(server, aObjectName, ISimpleMXBean.class);
//        return simpleBean;
    }

    /**
     * Gets the mxbean for our remote object using the default name "simpleBean"
     */
    protected ISimpleMXBean getSimpleMXBean() throws MalformedObjectNameException {
        return getMXBean(makeObjectName("simpleBean"));
    }

    /**
     * Makes an ObjectName for the given domain using our domain and the name attribute.
     */
    protected ObjectName makeObjectName(String aName) throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName(DOMAIN, NAME, aName);
        return objectName;
    }

    /**
     * Gets the body of the received message at the specified index
     */
    protected <T> T getBody(int aIndex, Class<T> aType) {
        Message in = getMessage(aIndex);
        T body = in.getBody(aType);
        assertNotNull(body);
        return body;
    }

    /**
     * Gets the received message at the specified index
     */
    protected Message getMessage(int aIndex) {
        Exchange exchange = getExchange(aIndex);
        Message in = exchange.getIn();
        return in;
    }

    /**
     * Gets the received exchange at the specified index
     */
    protected Exchange getExchange(int aIndex) {
        List<Exchange> exchanges = mMockEndpoint.getReceivedExchanges();
        Exchange exchange = exchanges.get(aIndex);
        return exchange;
    }

    /**
     * Creates the bean and registers it within the mbean server.
     * Note that we're using a fixed timestamp here to simplify the assertions in the tests
     */
    protected void initBean() throws Exception {
        registerBean(createSimpleBean(), makeObjectName("simpleBean"));
    }

    protected SimpleBean createSimpleBean() throws ParseException {
        SimpleBean simpleBean = new SimpleBean();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-dd-MM'T'HH:mm:ss");
        Date date = sdf.parse("2010-07-01T10:30:15");
        simpleBean.setTimestamp(date.getTime());
        return simpleBean;
    }

    /**
     * Initializes the camel context by creating a simple route from our mbean
     * to the mock endpoint.
     */
    protected void initContext() throws Exception {
        mMockEndpoint = (MockEndpoint) mContext.getEndpoint("mock:sink");
        mMockEndpoint.setExpectedMessageCount(1);
        mContext.setRegistry(getRegistry());
        mContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(buildFromURI().toString()).to(mMockEndpoint);
            }
        });
    }

    /**
     * Override this to control the properties that make up the endpoint
     */
    protected JMXUriBuilder buildFromURI() {
        JMXUriBuilder uri = new JMXUriBuilder().withObjectDomain(DOMAIN)
                .withObjectName("simpleBean");
        return uri;
    }

    /**
     * Override this to put stuff into the registry so it's available to be
     * referenced. (i.e. NotificationFilter or Hashtable<String,String> for ObjectProperties
     */
    protected void initRegistry() {
    }

    protected DefaultCamelContext getContext() {
        return mContext;
    }

    protected void setContext(DefaultCamelContext aContext) {
        mContext = aContext;
    }

    protected SimpleRegistry getRegistry() {
        return mRegistry;
    }

    protected void setRegistry(SimpleRegistry aRegistry) {
        mRegistry = aRegistry;
    }

    protected MockEndpoint getMockEndpoint() {
        return mMockEndpoint;
    }

    protected void setMockEndpoint(MockEndpoint aMockEndpoint) {
        mMockEndpoint = aMockEndpoint;
    }

    /**
     * Assert that we've received the message and resets the mock endpoint
     */
    protected void assertMessageReceived(File aExpectedFile) throws Exception {
        Document actual = XmlFixture.toDoc(getBody(0, String.class));
        Document noTime = XmlFixture.stripTimestamp(actual);
        XmlFixture.assertXMLIgnorePrefix("failed to match",
                XmlFixture.toDoc(aExpectedFile),
                noTime);
        // assert that we have a timestamp and datetime
        // can't rely on the datetime being the same due to timezone differences
        // instead, we'll assert that the values exist.
        XPathFactory xpf = XPathFactory.newInstance();
        XPath xp = xpf.newXPath();
        xp.setNamespaceContext(new NamespaceContext(){
            public String getNamespaceURI(String aArg0) {
                return "urn:org.apache.camel.component:jmx";
            }
            public String getPrefix(String aArg0) {
                return "jmx";
            }
            public Iterator getPrefixes(String aArg0) {
                return null;
            }
        });
        assertEquals("1262878215000", xp.evaluate("string(//jmx:timestamp)", actual));
        assertEquals("1", xp.evaluate("count(//jmx:dateTime)", actual));
        resetMockEndpoint();
    }

    /**
     * Resets the mock endpoint so we can run another test. This will clear out any
     * previously received messages.
     */
    protected void resetMockEndpoint() {
        getMockEndpoint().reset();
    }
}