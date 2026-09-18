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
package org.apache.camel.component.alibaba.common;

import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.util.ObjectHelper;

public final class OpenApiClientSupport {

    private OpenApiClientSupport() {
    }

    public static String resolveAccessKey(String accessKey, ServiceKeys serviceKeys) {
        if (ObjectHelper.isNotEmpty(accessKey)) {
            return accessKey;
        }
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getAccessKey())) {
            return serviceKeys.getAccessKey();
        }
        throw new IllegalArgumentException("Authentication parameter 'access key (AK)' not found");
    }

    public static String resolveSecretKey(String secretKey, ServiceKeys serviceKeys) {
        if (ObjectHelper.isNotEmpty(secretKey)) {
            return secretKey;
        }
        if (serviceKeys != null && ObjectHelper.isNotEmpty(serviceKeys.getSecretKey())) {
            return serviceKeys.getSecretKey();
        }
        throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
    }

    public static String resolveString(Exchange exchange, String name, String endpointValue) {
        String value = exchange.getIn().getHeader(name, String.class);
        if (ObjectHelper.isEmpty(value)) {
            value = exchange.getProperty(name, String.class);
        }
        if (ObjectHelper.isEmpty(value)) {
            value = endpointValue;
        }
        return value;
    }

    public static Integer resolveInteger(Exchange exchange, String name, Integer endpointValue) {
        Integer value = exchange.getIn().getHeader(name, Integer.class);
        if (value == null) {
            value = exchange.getProperty(name, Integer.class);
        }
        if (value == null) {
            value = endpointValue;
        }
        return value;
    }

    public static Boolean resolveBoolean(Exchange exchange, String name, Boolean endpointValue) {
        Boolean value = exchange.getIn().getHeader(name, Boolean.class);
        if (value == null) {
            value = exchange.getProperty(name, Boolean.class);
        }
        if (value == null) {
            value = endpointValue;
        }
        return value != null ? value : false;
    }

    public static Long resolveLong(Exchange exchange, String name, Long endpointValue) {
        Long value = exchange.getIn().getHeader(name, Long.class);
        if (value == null) {
            value = exchange.getProperty(name, Long.class);
        }
        if (value == null) {
            value = endpointValue;
        }
        return value;
    }
}
