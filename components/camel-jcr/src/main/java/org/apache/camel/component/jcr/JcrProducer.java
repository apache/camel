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

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Calendar;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
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
        TypeConverter converter = exchange.getContext().getTypeConverter();
        Session session = openSession();
        String operation = determineOperation(exchange);
        try {
            if (JcrConstants.JCR_INSERT.equals(operation)) {
                Node base = findOrCreateNode(session.getRootNode(), getJcrEndpoint().getBase());
                Node node = findOrCreateNode(base, getNodeName(exchange));
                for (String key : exchange.getProperties().keySet()) {
                    Value value = converter.convertTo(Value.class, exchange, exchange.getProperty(key));
                    node.setProperty(key, value);
                }
                node.addMixin("mix:referenceable");
                exchange.getOut().setBody(node.getIdentifier());
            } else if (JcrConstants.JCR_GET_BY_ID.equals(operation)) {
                Node node = session.getNodeByIdentifier(exchange.getIn()
                        .getMandatoryBody(String.class));
                PropertyIterator properties = node.getProperties();
                while (properties.hasNext()) {
                    Property property = properties.nextProperty();
                    Class<?> aClass = classForJCRType(property);
                    Object value = converter.convertTo(aClass, exchange, property.getValue());
                    exchange.setProperty(property.getName(), value);
                }
            } else {
                throw new RuntimeException("Unsupported operation: " + operation);
            }

        } finally {
            session.save();
            if (session != null && session.isLive()) {
                session.logout();
            }
        }
    }

    private Class<?> classForJCRType(Property property) throws RepositoryException {
        switch (property.getType()) {
        case PropertyType.STRING:
            return String.class;
        case PropertyType.BINARY:
            return InputStream.class;
        case PropertyType.BOOLEAN:
            return Boolean.class;
        case PropertyType.LONG:
            return Long.class;
        case PropertyType.DOUBLE:
            return Double.class;
        case PropertyType.DECIMAL:
            return BigDecimal.class;
        case PropertyType.DATE:
            return Calendar.class;
        case PropertyType.NAME:
            return String.class;
        case PropertyType.PATH:
            return String.class;
        case PropertyType.REFERENCE:
            return String.class;
        case PropertyType.WEAKREFERENCE:
            return String.class;
        case PropertyType.URI:
            return String.class;
        case PropertyType.UNDEFINED:
            return String.class;
        default:
            throw new IllegalArgumentException("unknown type: " + property.getType());
        }
    }

    private String determineOperation(Exchange exchange) {
        String operation = exchange.getIn().getHeader(JcrConstants.JCR_OPERATION, String.class);
        return operation != null ? operation : JcrConstants.JCR_INSERT;
    }

    private String getNodeName(Exchange exchange) {
        if (exchange.getProperty(JcrConstants.JCR_NODE_NAME) != null) {
            return exchange.getProperty(JcrConstants.JCR_NODE_NAME, String.class);
        }
        return exchange.getExchangeId();
    }

    private Node findOrCreateNode(Node parent, String path) throws RepositoryException {
        Node result = parent;
        for (String component : path.split("/")) {
            component = Text.escapeIllegalJcrChars(component);
            if (component.length() > 0) {
                if (result.hasNode(component)) {
                    result = result.getNode(component);
                } else {
                    result = result.addNode(component);
                }
            }
        }
        return result;
    }

    protected Session openSession() throws RepositoryException {
        return getJcrEndpoint().getRepository().login(getJcrEndpoint().getCredentials());
    }

    private JcrEndpoint getJcrEndpoint() {
        return (JcrEndpoint)getEndpoint();
    }
}
