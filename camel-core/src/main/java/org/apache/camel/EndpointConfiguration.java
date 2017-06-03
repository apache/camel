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
package org.apache.camel;

import java.net.URI;

/**
 * Holds an {@link Endpoint} configuration as a pojo that can be manipulated and validated.
 * Camel endpoint configuration is strongly related to URIs.
 */
@Deprecated
public interface EndpointConfiguration {

    String URI_SCHEME               = "scheme";
    String URI_SCHEME_SPECIFIC_PART = "schemeSpecificPart";
    String URI_AUTHORITY            = "authority";
    String URI_USER_INFO            = "userInfo";
    String URI_HOST                 = "host";
    String URI_PORT                 = "port";
    String URI_PATH                 = "path";
    String URI_QUERY                = "query";
    String URI_FRAGMENT             = "fragment";

    /**
     * {@link org.apache.camel.spi.DataFormat} operations.
     */
    enum UriFormat {
        Canonical, Provider, Consumer, Complete
    }

    /**
     * Returns the URI configuration of an {@link Endpoint}.
     *
     * @return the configuration URI.
     */
    URI getURI();

    /**
     * Gets the value of a particular parameter.
     *
     * @param name the parameter name
     * @return the configuration URI.
     * @throws RuntimeCamelException is thrown if error getting the parameter
     */
    <T> T getParameter(String name) throws RuntimeCamelException;

    /**
     * Sets the value of a particular parameter.
     *
     * @param name  the parameter name
     * @param value the parameter value
     * @throws RuntimeCamelException is thrown if error setting the parameter
     */
    <T> void setParameter(String name, T value) throws RuntimeCamelException;

    /**
     * Returns the formatted configuration string of an {@link Endpoint}.
     *
     * @param format the format
     * @return the configuration URI in String format.
     */
    String toUriString(UriFormat format);
}
