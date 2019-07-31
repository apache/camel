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
package org.apache.camel.component.apns.util;

public final class ParamUtils {

    private ParamUtils() {
    }

    public static void checkNotNull(Object param, String paramValue) {
        AssertUtils.notNull(paramValue, param + " cannot be null");
    }

    public static void checkNotEmpty(String paramValue, String paramName) {
        AssertUtils.notNull(paramValue, paramName + " cannot be null");
        AssertUtils.isTrue(StringUtils.isNotEmpty(paramValue), paramName + " cannot be empty");
    }

    public static void checkDestination(String host, int port, String paramName) {
        if ((StringUtils.isEmpty(host) && port != 0) || (StringUtils.isNotEmpty(host) && port == 0)) {
            throw new IllegalArgumentException(paramName + "host and port parameters are not coherent: host=" + host + ", port=" + port);
        }
    }

}
