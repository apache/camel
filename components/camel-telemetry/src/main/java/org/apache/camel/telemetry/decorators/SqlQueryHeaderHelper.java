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
package org.apache.camel.telemetry.decorators;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Determines whether an endpoint honours the {@code CamelSqlQuery} header.
 * <p/>
 * The camel-sql components gate that header behind the {@code allowQueryFromHeader} option, which is disabled by
 * default. When it is disabled the header is ignored and the endpoint-configured query is executed instead, so
 * surfacing the header value would attribute a statement to the exchange that never ran, and would place
 * sender-controlled text into telemetry.
 * <p/>
 * The option is read through the endpoint's generated {@link PropertyConfigurer} rather than by casting, because the
 * tracing modules must not depend on camel-sql. An endpoint that does not declare the option at all (jdbc, for
 * instance) never honours the header.
 */
final class SqlQueryHeaderHelper {

    private static final String ALLOW_QUERY_FROM_HEADER = "allowQueryFromHeader";

    private SqlQueryHeaderHelper() {
    }

    static boolean isQueryHeaderHonoured(Endpoint endpoint) {
        if (!(endpoint instanceof DefaultEndpoint defaultEndpoint)) {
            return false;
        }
        Component component = defaultEndpoint.getComponent();
        if (component == null) {
            return false;
        }
        if (component.getEndpointPropertyConfigurer() instanceof PropertyConfigurerGetter getter) {
            return Boolean.TRUE.equals(getter.getOptionValue(endpoint, ALLOW_QUERY_FROM_HEADER, true));
        }
        return false;
    }
}
