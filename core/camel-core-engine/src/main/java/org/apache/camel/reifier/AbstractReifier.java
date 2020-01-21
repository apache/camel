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
package org.apache.camel.reifier;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

public abstract class AbstractReifier {

    protected static String parseString(RouteContext routeContext, String text) {
        return CamelContextHelper.parseText(routeContext.getCamelContext(), text);
    }

    protected static boolean parseBoolean(RouteContext routeContext, String text) {
        Boolean b = CamelContextHelper.parseBoolean(routeContext.getCamelContext(), text);
        return b != null && b;
    }

    protected static boolean parseBoolean(CamelContext camelContext, String text) {
        Boolean b = CamelContextHelper.parseBoolean(camelContext, text);
        return b != null && b;
    }

    protected static Long parseLong(RouteContext routeContext, String text) {
        return CamelContextHelper.parseLong(routeContext.getCamelContext(), text);
    }

    protected static Integer parseInt(RouteContext routeContext, String text) {
        return CamelContextHelper.parseInteger(routeContext.getCamelContext(), text);
    }

    protected static <T> T parse(RouteContext routeContext, Class<T> clazz, String text) {
        return CamelContextHelper.parse(routeContext.getCamelContext(), clazz, text);
    }

}
