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
package org.apache.camel.component.docker;

import java.lang.reflect.Array;

import org.apache.camel.Message;
import org.apache.camel.util.StringHelper;
import org.apache.commons.lang.BooleanUtils;

/**
 * Utility methods for Docker Component
 */
public final class DockerHelper {

    private static final String STRING_DELIMITER = ";";

    private DockerHelper() {
        // Helper class
    }

    /**
     * Transforms a Docker Component header value to its analogous URI
     * parameter
     *
     * @param name
     * @return
     */
    public static String transformFromHeaderName(String name) {
        StringHelper.notEmpty(name, "name");

        StringBuilder formattedName = new StringBuilder();

        String nameSubstring = name.substring(DockerConstants.DOCKER_PREFIX.length());

        if (nameSubstring.length() > 0) {
            formattedName.append(nameSubstring.substring(0, 1).toLowerCase());
            formattedName.append(nameSubstring, 1, nameSubstring.length());
        }

        return formattedName.toString();
    }

    /**
     * Attempts to locate a given property name within a URI parameter or the
     * message header. A found value in a message header takes precedence over a
     * URI parameter.
     *
     * @param name
     * @param configuration
     * @param message
     * @param clazz
     * @return
     */
    public static <T> T getProperty(String name, DockerConfiguration configuration, Message message, Class<T> clazz) {
        return getProperty(name, configuration, message, clazz, null);
    }

    /**
     * Attempts to locate a given property name within a URI parameter or the
     * message header. A found value in a message header takes precedence over a
     * URI parameter. Returns a default value if given
     *
     * @param name
     * @param configuration
     * @param message
     * @param clazz
     * @param defaultValue
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T getProperty(String name, DockerConfiguration configuration, Message message, Class<T> clazz, T defaultValue) {
        // First attempt to locate property from Message Header, then fallback
        // to Endpoint property

        if (message != null) {
            T headerProperty = message.getHeader(name, clazz);

            if (headerProperty != null) {
                return headerProperty;
            }
        }

        Object prop = configuration.getParameters().get(transformFromHeaderName(name));

        if (prop != null) {

            if (prop.getClass().isAssignableFrom(clazz)) {
                return (T)prop;
            } else if (Integer.class == clazz) {
                return (T)Integer.valueOf((String)prop);
            } else if (Boolean.class == clazz) {
                return (T)BooleanUtils.toBooleanObject((String)prop, "true", "false", "null");
            }
        } else if (defaultValue != null) {
            return defaultValue;
        }

        return null;
    }

    /**
     * Attempts to locate a given property which is an array by name within a
     * URI parameter or the message header. A found value in a message header
     * takes precedence over a URI parameter.
     *
     * @param name
     * @param message
     * @param clazz
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] getArrayProperty(String name, Message message, Class<T> clazz) {

        if (message != null) {
            Object header = message.getHeader(name);

            if (header != null) {
                if (header.getClass().isAssignableFrom(clazz)) {

                    T[] headerArray = (T[])Array.newInstance(clazz, 1);
                    headerArray[0] = (T)header;
                    return headerArray;

                }

                if (header.getClass().isArray()) {
                    if (header.getClass().getComponentType().isAssignableFrom(clazz) || header.getClass().getDeclaringClass().isAssignableFrom(clazz)) {
                        return (T[])header;
                    }
                }
            }

        }

        return null;
    }

    /**
     * @param headerName name of the header
     * @param message the Camel message
     * @return
     */
    public static String[] parseDelimitedStringHeader(String headerName, Message message) {

        Object header = message.getHeader(headerName);

        if (header != null) {

            if (header instanceof String) {
                return ((String)header).split(STRING_DELIMITER);
            }

            if (header instanceof String[]) {
                return (String[])header;
            }
        }

        return null;
    }

}
