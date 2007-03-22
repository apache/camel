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
package org.apache.camel.builder.xpath;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathVariableResolver;

/**
 * A variable resolver for XPath expressions which support properties on the messge, exchange as well
 * as making system properties and environment properties available.
 *
 * @version $Revision$
 */
public class MessageVariableResolver implements XPathVariableResolver {
    public static final String SYSTEM_PROPERTIES_NAMESPACE = "http://camel.apache.org/xml/variables/system-properties";
    public static final String ENVIRONMENT_VARIABLES = "http://camel.apache.org/xml/variables/environment-variables";

    private Exchange exchange;

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public Object resolveVariable(QName name) {
        // should we use other namespaces maybe?
        String uri = name.getNamespaceURI();
        String localPart = name.getLocalPart();

        Object answer = null;

        if (uri == null || uri.length() == 0) {
            Message message = exchange.getIn();
            if (message != null) {
                answer = message.getHeader(localPart);
            }
            if (answer == null) {
                answer = exchange.getProperty(localPart);
            }
        }
        else if (uri.equals(SYSTEM_PROPERTIES_NAMESPACE)) {
            answer = System.getProperty(localPart);
        }
        else if (uri.equals(ENVIRONMENT_VARIABLES)) {
            answer = System.getenv().get(localPart);
        }
        return answer;
    }
}
