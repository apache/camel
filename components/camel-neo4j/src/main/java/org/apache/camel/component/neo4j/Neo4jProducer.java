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
package org.apache.camel.component.neo4j;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.GraphDatabase;
import org.springframework.data.neo4j.support.Neo4jTemplate;

public class Neo4jProducer extends DefaultProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jProducer.class);

    private final Neo4jEndpoint endpoint;

    private final GraphDatabase graphDatabase;

    private final Neo4jTemplate template;

    public Neo4jProducer(Neo4jEndpoint endpoint, GraphDatabase graphDatabase) {
        super(endpoint);
        this.endpoint = endpoint;
        this.graphDatabase = graphDatabase;
        this.template = new Neo4jTemplate(graphDatabase);
    }

    public Neo4jProducer(Neo4jEndpoint endpoint, GraphDatabase graphDatabase, Neo4jTemplate template) {
        super(endpoint);
        this.endpoint = endpoint;
        this.graphDatabase = graphDatabase;
        this.template = template;
    }

    @SuppressWarnings("unchecked")
    Node createNode(Object body) {
        if (body == null) {
            return template.createNode();
        } else if (body instanceof Map) {
            return template.createNode((Map<String, Object>)body);
        }
        throw new Neo4jException("Unsupported body type for create node [" + body.getClass() + "]");
    }
    
    Relationship createRelationship(Object body) {

        if (body instanceof SpringDataRelationship) {
            SpringDataRelationship<?> r = (SpringDataRelationship<?>)body;
            Object rr = template.createRelationshipBetween(r.getStart(), r.getEnd(),
                                                           r.getRelationshipEntityClass(),
                                                           r.getRelationshipType(), r.isAllowDuplicates());
            return (Relationship)rr;

        } else if (body instanceof BasicRelationship) {
            BasicRelationship r = (BasicRelationship)body;
            Object rr = template.createRelationshipBetween(r.getStart(), r.getEnd(), r.getRelationshipType(),
                                                           r.getProperties());
            return (Relationship)rr;
        }
        throw new Neo4jException("Unsupported body type for create relationship [" + body == null
            ? "null" : body.getClass() + "]");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        Neo4jOperation op = (Neo4jOperation)exchange.getIn().getHeader(Neo4jEndpoint.HEADER_OPERATION);
        if (op == null) {
            throw new Neo4jException("No operation specified for exchange " + exchange);
        }

        switch (op) {
        case CREATE_NODE:
            Node node = createNode(body);
            LOGGER.debug("Node created [{}]", node);
            exchange.getIn().setHeader(Neo4jEndpoint.HEADER_NODE_ID, node.getId());
            break;
        case CREATE_RELATIONSHIP:
            Relationship r = createRelationship(body);
            LOGGER.debug("Relationship created [{}]", r);
            exchange.getIn().setHeader(Neo4jEndpoint.HEADER_RELATIONSHIP_ID, r.getId());
            break;
        case REMOVE_NODE:
            removeNode(body);
            break;
        case REMOVE_RELATIONSHIP:
            removeRelationship(body);
            break;
        default:
            // do nothing here.
        }
    }

    void removeNode(Object body) {
        if (body instanceof Number) {
            LOGGER.debug("Deleting node by id [" + body + "]");
            Node node = template.getNode(((Number)body).longValue());
            template.delete(node);
        } else if (body instanceof Node) {
            template.delete(body);
        } else {
            throw new Neo4jException("Unsupported body type for remove node [" + body == null
                ? "null" : body.getClass() + "]");
        }
    }

    void removeRelationship(Object body) {
        if (body instanceof Number) {
            LOGGER.debug("Deleting relationship by id [" + body + "]");
            Relationship r = template.getRelationship(((Number)body).longValue());
            template.delete(r);
        } else if (body instanceof Relationship) {
            template.delete(body);
        } else if (body instanceof SpringDataRelationship) {
            SpringDataRelationship<?> r = (SpringDataRelationship<?>)body;
            template.deleteRelationshipBetween(r.getStart(), r.getEnd(), r.getRelationshipType());
        } else {
            throw new Neo4jException("Unsupported body type for remove node [" + body == null
                ? "null" : body.getClass() + "]");
        }
    }
}
