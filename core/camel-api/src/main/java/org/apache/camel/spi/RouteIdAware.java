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
package org.apache.camel.spi;

/**
 * To allow objects to be injected with the route id
 * <p/>
 * This allows access to the route id of the processor at runtime, to know which route its associated with.
 */
public interface RouteIdAware {

    /**
     * Gets the route id
     */
    String getRouteId();

    /**
     * Sets the route id
     *
     * @param routeId the route id
     */
    void setRouteId(String routeId);

}
