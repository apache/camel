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
package org.apache.camel.component.jcr;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultProducer;
import org.apache.jackrabbit.util.Text;

public class JcrProducer extends DefaultProducer {

    public JcrProducer(JcrEndpoint jcrEndpoint) throws RepositoryException {
        super(jcrEndpoint);
    }

    public void process(Exchange exchange) throws Exception {
        Session session = openSession();
        try {
            Node base = findOrCreateNode(session.getRootNode(),
                    getJcrEndpoint().getBase());
            Node node = findOrCreateNode(base, getNodeName(exchange));
            TypeConverter converter = exchange.getContext().getTypeConverter();
            for (String key : exchange.getProperties().keySet()) {
                Value value = converter.convertTo(Value.class, exchange,
                        exchange.getProperty(key));
                node.setProperty(key, value);
            }
            node.addMixin("mix:referenceable");
            session.save();
            exchange.getOut().setBody(node.getUUID());
        } finally {
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    private String getNodeName(Exchange exchange) {
        if (exchange.getProperty(JcrConstants.JCR_NODE_NAME) != null) {
            return exchange.getProperty(JcrConstants.JCR_NODE_NAME,
                    String.class);
        }
        return exchange.getExchangeId();
    }

    private Node findOrCreateNode(Node parent, String path)
            throws RepositoryException {
        Node result = parent;
        for (String component : path.split("/")) {
            component = Text.escapeIllegalJcrChars(component);
            if (component.length() > 0 && !result.hasNode(component)) {
                result = result.addNode(component);
            }
        }
        return result;
    }

    protected Session openSession() throws RepositoryException {
        return getJcrEndpoint().getRepository().login(
                getJcrEndpoint().getCredentials());
    }

    private JcrEndpoint getJcrEndpoint() {
        JcrEndpoint endpoint = (JcrEndpoint) getEndpoint();
        return endpoint;
    }
}
