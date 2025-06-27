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
package org.apache.camel.component.azure.servicebus;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;

public class ServiceBusHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(
            Boolean.class,
            Byte.class,
            Character.class,
            Double.class,
            Float.class,
            Integer.class,
            Long.class,
            Short.class,
            String.class,
            Date.class,
            UUID.class);

    public ServiceBusHeaderFilterStrategy() {
        super();
        setLowerCase(true);
        setOutFilterStartsWith(DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH);
        setInFilterStartsWith(DefaultHeaderFilterStrategy.CAMEL_FILTER_STARTS_WITH);
    }

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        return headerValue == null || !SUPPORTED_TYPES.contains(headerValue.getClass())
                || super.applyFilterToCamelHeaders(headerName, headerValue, exchange);
    }
}
