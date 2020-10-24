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
package org.apache.camel.model;

/**
 * Strategy for model definitions notifications.
 *
 * A custom strategy must be added to {@link ModelCamelContext} before any routes or route templates are added. In other
 * words add your custom strategy as early as possible.
 */
public interface ModelLifecycleStrategy {

    /**
     * Notification when a route definition is being added to {@link org.apache.camel.CamelContext}
     *
     * @param routeDefinition the route definition
     */
    void onAddRouteDefinition(RouteDefinition routeDefinition);

    /**
     * Notification when a route definition is being removed from {@link org.apache.camel.CamelContext}
     *
     * @param routeDefinition the route definition
     */
    void onRemoveRouteDefinition(RouteDefinition routeDefinition) throws Exception;

    /**
     * Notification when a route template definition is added to {@link org.apache.camel.CamelContext}
     *
     * @param routeTemplateDefinition the route template definition
     */
    void onAddRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition);

    /**
     * Notification when a route template definition is removed from {@link org.apache.camel.CamelContext}
     *
     * @param routeTemplateDefinition the route template definition
     */
    void onRemoveRouteTemplateDefinition(RouteTemplateDefinition routeTemplateDefinition);

}
