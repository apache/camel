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

import org.neo4j.graphdb.Node;

public class BasicRelationship {

    private final Node start;
    private final Node end;
    private final String relationshipType;
    private final Map<String, Object> properties;

    public BasicRelationship(Node start, Node end, String relationshipType) {
        this(start, end, relationshipType, null);
    }

    public BasicRelationship(Node start, Node end, String relationshipType, Map<String, Object> properties) {
        this.start = start;
        this.end = end;
        this.relationshipType = relationshipType;
        this.properties = properties;
    }

    public Node getEnd() {
        return end;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public Node getStart() {
        return start;
    }
}
