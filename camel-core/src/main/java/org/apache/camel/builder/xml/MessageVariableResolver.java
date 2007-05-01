/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder.xml;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathVariableResolver;
import java.util.HashMap;
import java.util.Map;

/**
 * A variable resolver for XPath expressions which support properties on the messge, exchange as well
 * as making system properties and environment properties available.
 *
 * @version $Revision: 521692 $
 */
public class MessageVariableResolver implements XPathVariableResolver {
    public static final String SYSTEM_PROPERTIES_NAMESPACE = "http://camel.apache.org/xml/variables/system-properties";
    public static final String ENVIRONMENT_VARIABLES = "http://camel.apache.org/xml/variables/environment-variables";
    public static final String EXCHANGE_PROPERTY = "http://camel.apache.org/xml/variables/exchange-property";
    public static final String IN_HEADER = "http://camel.apache.org/xml/variables/in-header";
    public static final String OUT_HEADER = "http://camel.apache.org/xml/variables/out-header";

    private static final transient Log log = LogFactory.getLog(MessageVariableResolver.class);

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

        if (uri == null || uri.length() == 0) {
            answer = variables.get(localPart);
            if (answer == null) {
                Message message = exchange.getIn();
                if (message != null) {
                    answer = message.getHeader(localPart);
                }
                if (answer == null) {
                    answer = exchange.getProperty(localPart);
                }
            }
        }
        else if (uri.equals(SYSTEM_PROPERTIES_NAMESPACE)) {
            try {
                answer = System.getProperty(localPart);
            }
            catch (Exception e) {
                log.debug("Security exception evaluating system property: " + localPart + ". Reason: " + e, e);
            }
        }
        else if (uri.equals(ENVIRONMENT_VARIABLES)) {
            answer = System.getenv().get(localPart);
        }
        else if (uri.equals(EXCHANGE_PROPERTY)) {
            answer = exchange.getProperty(localPart);
        }
        else if (uri.equals(IN_HEADER)) {
            answer = exchange.getIn().getHeader(localPart);
        }
        else if (uri.equals(OUT_HEADER)) {
            answer = exchange.getOut().getHeader(localPart);
        }

        // TODO support exposing CamelContext properties/resources via XPath?
        return answer;
    }

    public void addVariable(String localPart, Object value) {
        variables.put(localPart, value);
    }
}
