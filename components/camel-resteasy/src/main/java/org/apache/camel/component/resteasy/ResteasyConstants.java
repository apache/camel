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
package org.apache.camel.component.resteasy;

/**
 * Constants used in the Resteasy component
 *
 */
public final class ResteasyConstants {
    public static final String RESTEASY_PROXY_METHOD = "CamelResteasyProxyMethod";
    public static final String RESTEASY_PROXY_METHOD_PARAMS = "CamelResteasyProxyMethodArgs";
    public static final String RESTEASY_USERNAME = "CamelResteasyLogin";
    public static final String RESTEASY_PASSWORD = "CamelResteasyPassword";
    public static final String RESTEASY_CONTEXT_PATH = "CamelResteasyContextPath";
    public static final String RESTEASY_RESPONSE = "CamelResteasyResponse";
    public static final String RESTEASY_HTTP_METHOD = "CamelResteasyHttpMethod";
    public static final String RESTEASY_HTTP_REQUEST = "CamelResteasyHttpRequest";
    public static final String RESTEASY_PROXY_PRODUCER_EXCEPTION = "CamelResteasyProxyProducerException";

    /**
     * Utility classes should not have a public constructor.
     */
    private ResteasyConstants() {
    }
}
