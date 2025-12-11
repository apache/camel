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
package org.apache.camel.component.jms;

import java.util.HashMap;
import java.util.Map;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.spi.BeanIntrospection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.ObjectHelper.invokeMethodSafe;

final class JmsServiceLocationHelper {

    private static final Logger LOG = LoggerFactory.getLogger(JmsServiceLocationHelper.class);

    private JmsServiceLocationHelper() {
    }

    public static String getBrokerURLFromConnectionFactory(BeanIntrospection bi, ConnectionFactory cf) {
        // try to find the brokerURL for JMS broker such as Apache ActiveMQ and Artemis
        if (cf == null) {
            return null;
        }
        Map<String, Object> props = new HashMap<>();
        bi.getProperties(cf, props, null, false);
        Object url = props.get("brokerURL");
        if (url != null) {
            return url.toString();
        } else {
            // nested connection factory which can be wrapped in connection pooling
            ConnectionFactory ncf = (ConnectionFactory) props.get("connectionFactory");
            if (ncf != null) {
                return getBrokerURLFromConnectionFactory(bi, ncf);
            }
        }
        return artemisBrokerURL(cf);
    }

    public static String getUsernameFromConnectionFactory(BeanIntrospection bi, ConnectionFactory cf) {
        // try to find the brokerURL for JMS broker such as Apache ActiveMQ and Artemis
        if (cf == null) {
            return null;
        }
        Map<String, Object> props = new HashMap<>();
        bi.getProperties(cf, props, null, false);
        Object user = props.get("user");
        if (user == null) {
            user = props.get("username");
        }
        if (user == null) {
            user = props.get("userName");
        }
        if (user != null) {
            return user.toString();
        } else {
            // nested connection factory which can be wrapped in connection pooling
            ConnectionFactory ncf = (ConnectionFactory) props.get("connectionFactory");
            if (ncf != null) {
                return getUsernameFromConnectionFactory(bi, ncf);
            }
        }
        return artemisUsername(cf);
    }

    private static String artemisBrokerURL(ConnectionFactory cf) {
        try {
            // NOTE: the dependency has to be provided by final user.
            if ("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory".equals(cf.getClass().getName())) { // NOSONAR
                Object obj = invokeMethodSafe("getServerLocator", cf);
                if (obj != null) {
                    Object[] arr = (Object[]) invokeMethodSafe("getStaticTransportConfigurations", obj);
                    if (arr != null && arr.length > 0) {
                        obj = arr[0];
                        Map map = (Map) invokeMethodSafe("getParams", obj);
                        if (map != null) {
                            Object host = map.get("host");
                            Object port = map.get("port");
                            if (host != null && port != null) {
                                return host + ":" + port;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("An exception occurred while parsing broker url: ignoring", e);
        }
        return null;
    }

    private static String artemisUsername(ConnectionFactory cf) {
        try {
            // NOTE: the dependency has to be provided by final user.
            if ("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory".equals(cf.getClass().getName())) { // NOSONAR
                return (String) invokeMethodSafe("getUser", cf);
            }
        } catch (Exception e) {
            LOG.warn("An exception occurred while parsing username: ignoring", e);
        }
        return null;
    }

}
