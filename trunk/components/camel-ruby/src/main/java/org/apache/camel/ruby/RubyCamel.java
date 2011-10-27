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
package org.apache.camel.ruby;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * @version 
 */
public final class RubyCamel {
    
    private static CamelContext camelContext;
    private static List<RouteBuilder> routes = new ArrayList<RouteBuilder>();

    private RubyCamel() {
        // helper class
    }

    public static List<RouteBuilder> getRoutes() {
        return routes;
    }

    public static void addRouteBuilder(RouteBuilder builder) {
        routes.add(builder);
    }

    public static CamelContext getCamelContext() {
        if (camelContext == null) {
            camelContext = new DefaultCamelContext();
        }
        return camelContext;
    }

    public static void setCamelContext(CamelContext camelContext) {
        RubyCamel.camelContext = camelContext;
    }

    public static void clearRoutes() {
        routes.clear();
    }
}
