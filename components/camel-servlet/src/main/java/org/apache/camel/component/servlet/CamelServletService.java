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

import java.util.Set;

import org.apache.camel.component.http.CamelServlet;
import org.apache.camel.component.http.HttpConsumer;

/**
 * Service which binds {@link CamelServlet} to the consumers it should service.
 */
public interface CamelServletService {

    /**
     * Adds the given consumer to this service.
     *
     * @param consumer the consumer
     */
    void addConsumer(HttpConsumer consumer);

    /**
     * Gets the known consumers this service services.
     *
     * @return the consumers.
     */
    Set<HttpConsumer> getConsumers();

    /**
     * Sets the servlet to use.
     *
     * @param camelServlet the servlet to use.
     */
    void setCamelServlet(CamelServlet camelServlet);

    /**
     * Connect the given consumer to the servlet.
     *
     * @param consumer the consumer
     */
    void connect(HttpConsumer consumer);

    /**
     * Disconnects the given consumer from the servlet.
     *
     * @param consumer the consumer
     */
    void disconnect(HttpConsumer consumer);

    /**
     * Gets the name of the servlet used.
     *
     * @return the name of the servlet used.
     */
    String getServletName();

}
