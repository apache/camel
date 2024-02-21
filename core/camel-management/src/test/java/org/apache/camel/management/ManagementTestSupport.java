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
package org.apache.camel.management;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.NamedNode;
import org.apache.camel.spi.NodeIdFactory;

import static org.apache.camel.management.DefaultManagementAgent.DEFAULT_DOMAIN;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_CONTEXT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_NAME;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.KEY_TYPE;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_COMPONENT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_CONTEXT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ENDPOINT;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_STEP;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_THREAD_POOL;

/**
 * Base class for JMX tests.
 */
public abstract class ManagementTestSupport extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected boolean canRunOnThisPlatform() {
        // JMX tests dont work well on AIX CI servers (hangs them)
        return !isPlatform("aix");
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @SuppressWarnings("unchecked")
    protected <T> T invoke(MBeanServerConnection server, ObjectName name, String operationName)
            throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return (T) server.invoke(name, operationName, null, null);
    }

    @SuppressWarnings("unchecked")
    protected <T> T invoke(
            MBeanServerConnection server, ObjectName name, String operationName, Object[] params, String[] signature)
            throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
        return (T) server.invoke(name, operationName, params, signature);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getCamelContextExtension().addContextPlugin(NodeIdFactory.class, buildNodeIdFactory());
        return ctx;
    }

    private static NodeIdFactory buildNodeIdFactory() {
        return new NodeIdFactory() {
            private AtomicInteger counter = new AtomicInteger();

            @Override
            public String createId(NamedNode definition) {
                return definition.getShortName() + counter.incrementAndGet();
            }
        };
    }

    public ObjectName getContextObjectName() throws MalformedObjectNameException {
        return getCamelObjectName(TYPE_CONTEXT, context.getName());
    }

    public ObjectName getCamelObjectName(String type, String name) throws MalformedObjectNameException {
        String quote;
        switch (type) {
            case TYPE_CONTEXT:
            case TYPE_COMPONENT:
            case TYPE_ENDPOINT:
            case TYPE_PROCESSOR:
            case TYPE_ROUTE:
            case TYPE_THREAD_POOL:
            case TYPE_STEP:
                quote = "\"";
                break;
            default:
                quote = "";
                break;
        }
        String on = DEFAULT_DOMAIN + ":"
                    + KEY_CONTEXT + "=" + context.getManagementName() + ","
                    + KEY_TYPE + "=" + type + ","
                    + KEY_NAME + "=" + quote + name + quote;
        return ObjectName.getInstance(on);
    }
}
