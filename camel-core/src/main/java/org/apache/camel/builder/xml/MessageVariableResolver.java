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
package org.apache.camel.builder.xml;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathVariableResolver;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.builder.xml.Namespaces.ENVIRONMENT_VARIABLES;
import static org.apache.camel.builder.xml.Namespaces.EXCHANGE_PROPERTY;
import static org.apache.camel.builder.xml.Namespaces.IN_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.OUT_NAMESPACE;
import static org.apache.camel.builder.xml.Namespaces.SYSTEM_PROPERTIES_NAMESPACE;

/**
 * A variable resolver for XPath expressions which support properties on the
 * messge, exchange as well as making system properties and environment
 * properties available.
 *
 * @version $Revision$
 */
public class MessageVariableResolver implements XPathVariableResolver {
    private static final transient Log LOG = LogFactory.getLog(MessageVariableResolver.class);

    private Exchange exchange;
    private Map<String, Object> variables = new HashMap<String, Object>();

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public Object resolveVariable(QName name) {
        String uri = name.getNamespaceURI();
        String localPart = name.getLocalPart();
        Object answer = null;

        Message in = exchange.getIn();
        if (uri == null || uri.length() == 0) {
            answer = variables.get(localPart);
            if (answer == null) {
                Message message = in;
                if (message != null) {
                    answer = message.getHeader(localPart);
                }
                if (answer == null) {
                    answer = exchange.getProperty(localPart);
                }
            }
        } else if (uri.equals(SYSTEM_PROPERTIES_NAMESPACE)) {
            try {
                answer = System.getProperty(localPart);
            } catch (Exception e) {
                LOG.debug("Security exception evaluating system property: " + localPart
                          + ". Reason: " + e, e);
            }
        } else if (uri.equals(ENVIRONMENT_VARIABLES)) {
            answer = System.getenv().get(localPart);
        } else if (uri.equals(EXCHANGE_PROPERTY)) {
            answer = exchange.getProperty(localPart);
        } else if (uri.equals(IN_NAMESPACE)) {
            answer = in.getHeader(localPart);
            if (answer == null && localPart.equals("body")) {
                answer = in.getBody();
            }
        } else if (uri.equals(OUT_NAMESPACE)) {
            Message out = exchange.getOut(false);
            if (out != null) {
                answer = out.getHeader(localPart);
                if (answer == null && localPart.equals("body")) {
                    answer = out.getBody();
                }
            }
        }

        // If we can't find an answer we must return void.
        // We can't return null then the xpath engine will throw a NullPointerException
        if (answer == null) {
            return Void.class;
        } else {
            return answer;
        }
    }

    public void addVariable(String localPart, Object value) {
        variables.put(localPart, value);
    }
}
