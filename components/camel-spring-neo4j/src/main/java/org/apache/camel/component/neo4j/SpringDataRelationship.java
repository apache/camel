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

public class SpringDataRelationship<R> {

    private final Object start;
    private final Object end;
    private final Class<R> relationshipEntityClass;
    private final String relationshipType;
    private final boolean allowDuplicates;

    public SpringDataRelationship(Object start, Object end, Class<R> relationshipEntityClass,
                                  String relationshipType, boolean allowDuplicates) {
        this.start = start;
        this.end = end;
        this.relationshipEntityClass = relationshipEntityClass;
        this.relationshipType = relationshipType;
        this.allowDuplicates = allowDuplicates;
    }

    public Object getEnd() {
        return end;
    }

    public Class<R> getRelationshipEntityClass() {
        return relationshipEntityClass;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public Object getStart() {
        return start;
    }

    public boolean isAllowDuplicates() {
        return allowDuplicates;
    }
}
