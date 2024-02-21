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
package org.apache.camel.api.management;

import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedConsumerMBean;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedStepMBean;

public interface ManagedCamelContext {

    /**
     * Gets the managed Camel CamelContext client api
     */
    ManagedCamelContextMBean getManagedCamelContext();

    /**
     * Gets the managed processor client api from any of the routes which with the given id
     *
     * @param  id id of the processor
     * @return    the processor or <tt>null</tt> if not found
     */
    default ManagedProcessorMBean getManagedProcessor(String id) {
        return getManagedProcessor(id, ManagedProcessorMBean.class);
    }

    /**
     * Gets the managed processor client api from any of the routes which with the given id
     *
     * @param  id                       id of the processor
     * @param  type                     the managed processor type from the
     *                                  {@link org.apache.camel.api.management.mbean} package.
     * @return                          the processor or <tt>null</tt> if not found
     * @throws IllegalArgumentException if the type is not compliant
     */
    <T extends ManagedProcessorMBean> T getManagedProcessor(String id, Class<T> type);

    /**
     * Gets the managed step client api from any of the routes which with the given id
     *
     * @param  id id of the step
     * @return    the step or <tt>null</tt> if not found
     */
    ManagedStepMBean getManagedStep(String id);

    /**
     * Gets the managed route client api with the given route id
     *
     * @param  routeId id of the route
     * @return         the route or <tt>null</tt> if not found
     */
    default ManagedRouteMBean getManagedRoute(String routeId) {
        return getManagedRoute(routeId, ManagedRouteMBean.class);
    }

    /**
     * Gets the managed route client api with the given route id
     *
     * @param  routeId                  id of the route
     * @param  type                     the managed route type from the {@link org.apache.camel.api.management.mbean}
     *                                  package.
     * @return                          the route or <tt>null</tt> if not found
     * @throws IllegalArgumentException if the type is not compliant
     */
    <T extends ManagedRouteMBean> T getManagedRoute(String routeId, Class<T> type);

    /**
     * Gets the managed consumer client api from any of the routes which with the given route id
     *
     * @param  id route id having the consumer
     * @return    the consumer or <tt>null</tt> if not found
     */
    default ManagedConsumerMBean getManagedConsumer(String id) {
        return getManagedConsumer(id, ManagedConsumerMBean.class);
    }

    /**
     * Gets the managed consumer client api from any of the routes which with the given route id
     *
     * @param  id                       route id having the consumer
     * @param  type                     the managed consumer type from the {@link org.apache.camel.api.management.mbean}
     *                                  package.
     * @return                          the consumer or <tt>null</tt> if not found
     * @throws IllegalArgumentException if the type is not compliant
     */
    <T extends ManagedConsumerMBean> T getManagedConsumer(String id, Class<T> type);

}
