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

import java.util.Map;
import java.util.Set;

/**
 * Source for parameters used when creating routes from route templates.
 */
public interface RouteTemplateParameterSource {

    String TEMPLATE_ID = "templateId";

    String LOCATION = "location";

    /**
     * The parameters for the given route
     *
     * @param  routeId the route id
     * @return         the parameters for the route
     */
    Map<String, Object> parameters(String routeId);

    /**
     * Gets the route id's as a set.
     */
    Set<String> routeIds();

}
