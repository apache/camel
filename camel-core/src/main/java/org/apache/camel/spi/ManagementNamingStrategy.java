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
package org.apache.camel.spi;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.management.mbean.ManagedComponent;
import org.apache.camel.management.mbean.ManagedConsumer;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedProcessor;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.model.ProcessorDefinition;

/**
 * @version $Revision$
 */
public interface ManagementNamingStrategy {

    ObjectName getObjectName(CamelContext context) throws MalformedObjectNameException;

    ObjectName getObjectName(ManagedComponent mbean) throws MalformedObjectNameException;

    ObjectName getObjectName(ManagedEndpoint mbean) throws MalformedObjectNameException;

    ObjectName getObjectName(ManagedProcessor mbean) throws MalformedObjectNameException;

    ObjectName getObjectName(ManagedRoute mbean) throws MalformedObjectNameException;

    ObjectName getObjectName(ManagedConsumer mbean) throws MalformedObjectNameException;

    /**
     * @deprecated
     */
    ObjectName getObjectName(ManagedService mbean) throws MalformedObjectNameException;

    /**
     * @deprecated
     */
    ObjectName getObjectName(RouteContext routeContext, ProcessorDefinition processor) throws MalformedObjectNameException;

}
