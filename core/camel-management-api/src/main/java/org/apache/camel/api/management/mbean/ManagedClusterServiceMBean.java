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
package org.apache.camel.api.management.mbean;

import java.util.Collection;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedClusterServiceMBean {
    @ManagedAttribute(description = "The namespaces handled by the service")
    Collection<String> getNamespaces();

    @ManagedAttribute(description = "Service State")
    String getState();

    @ManagedAttribute(description = "Camel ID")
    String getCamelId();

    @ManagedOperation(description = "Start Service")
    void start() throws Exception;

    @ManagedOperation(description = "Stop Service")
    void stop() throws Exception;

    @ManagedOperation(description = "Start the View")
    void startView(String namespace) throws Exception;

    @ManagedOperation(description = "Stop the View")
    void stopView(String namespace) throws Exception;

    @ManagedOperation(description = "If the local view is leader")
    boolean isLeader(String namespace);
}
