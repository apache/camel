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
package org.apache.camel.component.servlet;

import org.apache.camel.http.common.CamelServlet;
import org.apache.camel.http.common.HttpConsumer;

/**
 * Keeps track of HttpConsumers and CamelServlets and 
 * connects them to each other. In OSGi there should
 * be one HttpRegistry per bundle.
 * 
 * A CamelServlet that should serve more than one
 * bundle should be registered as an OSGi service.
 * The HttpRegistryImpl can then be configured to listen
 * to service changes. See /tests/camel-itest-osgi/../servlet
 * for an example how to use this.
 */
public interface HttpRegistry {

    void register(HttpConsumer consumer);

    void unregister(HttpConsumer consumer);

    void register(CamelServlet provider);

    void unregister(CamelServlet provider);

    CamelServlet getCamelServlet(String servletName);

}