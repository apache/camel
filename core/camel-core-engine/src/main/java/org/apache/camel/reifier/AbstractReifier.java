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

    protected final RouteContext routeContext;
    protected final CamelContext camelContext;

    public AbstractReifier(RouteContext routeContext) {
        this.routeContext = routeContext;
        this.camelContext = routeContext.getCamelContext();
    }

    public AbstractReifier(CamelContext camelContext) {
        this.routeContext = null;
        this.camelContext = camelContext;
    }

    protected String parseString(String text) {
        return CamelContextHelper.parseText(camelContext, text);
    }

    protected boolean parseBoolean(String text) {
        Boolean b = CamelContextHelper.parseBoolean(camelContext, text);
        return b != null && b;
    }

    protected Long parseLong(String text) {
        return CamelContextHelper.parseLong(camelContext, text);
    }

    protected Integer parseInt(String text) {
        return CamelContextHelper.parseInteger(camelContext, text);
    }

    protected <T> T parse(Class<T> clazz, String text) {
        return CamelContextHelper.parse(camelContext, clazz, text);
    }

}
