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
package org.apache.camel.component.jmx;

import java.lang.management.ManagementFactory;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jmx.beans.ISimpleMXBean;
import org.apache.camel.component.jmx.beans.SimpleBean;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * MBean that is registered for the unit tests. The fixture will register a bean and provide access to the mxbean so
 * tests can invoke methods on the mxbean to trigger notifications.
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
    private MockEndpointFixture mMockEndpoint;

    @BeforeEach
    public void setUp() throws Exception {
        initServer();
        initBean();
        initRegistry();
        initContext();
        startContext();
    }

    protected void startContext() {
        mContext.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (!mContext.isStopped()) {
            mContext.stop();
        }
        unregisterBean(makeObjectName("simpleBean"));
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
        return MBeanServerInvocationHandler.newProxyInstance(
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
     * Creates the bean and registers it within the mbean server. Note that we're using a fixed timestamp here to
     * simplify the assertions in the tests
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
     * Initializes the camel context by creating a simple route from our mbean to the mock endpoint.
     */
    protected void initContext() throws Exception {
        final MockEndpoint mock = mContext.getEndpoint("mock:sink", MockEndpoint.class);
        mock.setExpectedMessageCount(1);
        mMockEndpoint = new MockEndpointFixture(mock);
        mContext.getCamelContextExtension().setRegistry(getRegistry());
        mContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(buildFromURI().toString()).to(mock);
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
     * Override this to put stuff into the registry so it's available to be referenced. (i.e. NotificationFilter or
     * Hashtable<String,String> for ObjectProperties
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

    protected MockEndpointFixture getMockFixture() {
        return mMockEndpoint;
    }
}
