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
import java.util.Locale;

import org.apache.camel.Message;
import org.apache.camel.util.StringHelper;
import org.apache.commons.lang3.BooleanUtils;

/**
 * Utility methods for Docker Component
 */
public final class DockerHelper {

    private static final String STRING_DELIMITER = ";";
    private static final String DOCKER_HUB = "docker.io";

    private DockerHelper() {
        // Helper class
    }

    /**
     * Transforms a Docker Component header value to its analogous URI parameter
     *
     * @param  name
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
     * Attempts to locate a given property name within a URI parameter or the message header. A found value in a message
     * header takes precedence over a URI parameter.
     *
     * @param  name
     * @param  configuration
     * @param  message
     * @param  clazz
     * @return
     */
    public static <T> T getProperty(String name, DockerConfiguration configuration, Message message, Class<T> clazz) {
        return getProperty(name, configuration, message, clazz, null);
    }

    /**
     * Attempts to locate a given property name within a URI parameter or the message header. A found value in a message
     * header takes precedence over a URI parameter. Returns a default value if given
     *
     * @param  name
     * @param  configuration
     * @param  message
     * @param  clazz
     * @param  defaultValue
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <
            T> T getProperty(String name, DockerConfiguration configuration, Message message, Class<T> clazz, T defaultValue) {
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
                return (T) prop;
            } else if (Integer.class == clazz) {
                return (T) Integer.valueOf((String) prop);
            } else if (Boolean.class == clazz) {
                return (T) BooleanUtils.toBooleanObject((String) prop, "true", "false", "null");
            }
        } else if (defaultValue != null) {
            return defaultValue;
        }

        return null;
    }

    /**
     * Attempts to locate a given property which is an array by name within a URI parameter or the message header. A
     * found value in a message header takes precedence over a URI parameter.
     *
     * @param  name
     * @param  message
     * @param  clazz
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T[] getArrayProperty(String name, Message message, Class<T> clazz) {

        if (message != null) {
            Object header = message.getHeader(name);

            if (header != null) {
                if (header.getClass().isAssignableFrom(clazz)) {

                    T[] headerArray = (T[]) Array.newInstance(clazz, 1);
                    headerArray[0] = (T) header;
                    return headerArray;

                }

                if (header.getClass().isArray()) {
                    if (header.getClass().getComponentType().isAssignableFrom(clazz)
                            || header.getClass().getDeclaringClass().isAssignableFrom(clazz)) {
                        return (T[]) header;
                    }
                }
            }

        }

        return null;
    }

    /**
     * Returns the registry host (with the port, if any) of an image reference, following the rules of Docker: the first
     * path component is a registry when it contains a dot or a colon, is <tt>localhost</tt> or has upper case letters;
     * otherwise the image is on Docker Hub. The names of Docker Hub are returned as <tt>docker.io</tt>, and the host is
     * in lower case.
     *
     * @param  image the image reference, such as <tt>ubuntu:24.04</tt> or <tt>myregistry:5000/team/app@sha256:...</tt>
     * @return       the registry host, or <tt>null</tt> if the image is empty
     */
    public static String getImageRegistryHost(String image) {
        if (image == null || image.isBlank()) {
            return null;
        }
        int slash = image.indexOf('/');
        if (slash == -1) {
            return DOCKER_HUB;
        }
        String first = image.substring(0, slash);
        if (first.indexOf('.') == -1 && first.indexOf(':') == -1 && !"localhost".equals(first)
                && first.equals(first.toLowerCase(Locale.ROOT))) {
            return DOCKER_HUB;
        }
        return normalizeRegistryHost(first);
    }

    /**
     * Returns the registry host (with the port, if any) of a registry server address, such as
     * <tt>https://index.docker.io/v1/</tt> or <tt>myregistry:5000</tt>. The scheme and the path are ignored, the names
     * of Docker Hub are returned as <tt>docker.io</tt>, and the host is in lower case.
     *
     * @param  serverAddress the server address of the registry
     * @return               the registry host, or <tt>null</tt> if the server address is empty
     */
    public static String getServerAddressRegistryHost(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            return null;
        }
        String host = serverAddress.trim();
        int scheme = host.indexOf("://");
        if (scheme != -1) {
            host = host.substring(scheme + 3);
        }
        int slash = host.indexOf('/');
        if (slash != -1) {
            host = host.substring(0, slash);
        }
        return host.isEmpty() ? null : normalizeRegistryHost(host);
    }

    private static String normalizeRegistryHost(String host) {
        String answer = host.toLowerCase(Locale.ROOT);
        if ("index.docker.io".equals(answer) || "registry-1.docker.io".equals(answer)) {
            return DOCKER_HUB;
        }
        return answer;
    }

    /**
     * @param  headerName name of the header
     * @param  message    the Camel message
     * @return
     */
    public static String[] parseDelimitedStringHeader(String headerName, Message message) {

        Object header = message.getHeader(headerName);

        if (header != null) {

            if (header instanceof String) {
                return ((String) header).split(STRING_DELIMITER);
            }

            if (header instanceof String[]) {
                return (String[]) header;
            }
        }

        return null;
    }

}
