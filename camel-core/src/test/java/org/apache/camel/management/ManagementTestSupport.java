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
package org.apache.camel.management;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.camel.ContextTestSupport;

/**
 * Base class for JMX tests.
 *
 * @version 
 */
public abstract class ManagementTestSupport extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @SuppressWarnings("unchecked")
    protected <T> T invoke(MBeanServer server, ObjectName name, String operationName)
        throws InstanceNotFoundException, MBeanException, ReflectionException {
        return (T)server.invoke(name, operationName, null, null);
    }

    @SuppressWarnings("unchecked")
    protected <T> T invoke(MBeanServer server, ObjectName name, String operationName, Object params[], String signature[])
            throws InstanceNotFoundException, MBeanException, ReflectionException {
        return (T)server.invoke(name, operationName, params, signature);
    }
}
