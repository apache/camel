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
package org.apache.camel.language.xpath;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathVariableResolver;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.builder.Namespaces.ENVIRONMENT_VARIABLES;
import static org.apache.camel.support.builder.Namespaces.EXCHANGE_PROPERTY;
import static org.apache.camel.support.builder.Namespaces.IN_NAMESPACE;
import static org.apache.camel.support.builder.Namespaces.OUT_NAMESPACE;
import static org.apache.camel.support.builder.Namespaces.SYSTEM_PROPERTIES_NAMESPACE;

/**
 * A variable resolver for XPath expressions which support properties on the message, exchange as well as making system
 * properties and environment properties available.
 * <p/>
 * Implementations of this resolver must be thread safe
 */
public class MessageVariableResolver implements XPathVariableResolver {
    private static final Logger LOG = LoggerFactory.getLogger(MessageVariableResolver.class);

    private Map<String, Object> variables = new HashMap<>();
    private final ThreadLocal<Exchange> exchange;

    public MessageVariableResolver(ThreadLocal<Exchange> exchange) {
        this.exchange = exchange;
    }

    @Override
    public Object resolveVariable(QName name) {
        String uri = name.getNamespaceURI();
        String localPart = name.getLocalPart();
        Object answer = null;

        Message in = exchange.get().getIn();
        if (uri == null || uri.length() == 0) {
            answer = variables.get(localPart);
            if (answer == null) {
                if (in != null) {
                    answer = in.getHeader(localPart);
                }
                if (answer == null) {
                    answer = exchange.get().getProperty(localPart);
                }
            }
        } else if (uri.equals(SYSTEM_PROPERTIES_NAMESPACE)) {
            try {
                answer = System.getProperty(localPart);
            } catch (Exception e) {
                LOG.debug("Security exception evaluating system property: {}. Reason: {}", localPart, e.getMessage(), e);
            }
        } else if (uri.equals(ENVIRONMENT_VARIABLES)) {
            answer = System.getenv().get(localPart);
        } else if (uri.equals(EXCHANGE_PROPERTY)) {
            answer = exchange.get().getProperty(localPart);
        } else if (uri.equals(IN_NAMESPACE)) {
            answer = in.getHeader(localPart);
            if (answer == null && localPart.equals("body")) {
                answer = in.getBody();
            }
        } else if (uri.equals(OUT_NAMESPACE)) {
            if (exchange.get().hasOut()) {
                Message out = exchange.get().getOut();
                answer = out.getHeader(localPart);
                if (answer == null && localPart.equals("body")) {
                    answer = out.getBody();
                }
            }
        }

        // if we can't find an answer we must return an empty String.
        // if we return null, then the JDK default XPathEngine will throw an exception
        if (answer == null) {
            return "";
        } else {
            return answer;
        }
    }

    public void addVariable(String localPart, Object value) {
        variables.put(localPart, value);
    }
}
