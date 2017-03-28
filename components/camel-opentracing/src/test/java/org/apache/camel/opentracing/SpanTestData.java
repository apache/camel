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
package org.apache.camel.opentracing;

public class SpanTestData {

    private String label;
    private String uri;
    private String operation;
    private String kind;
    private int parentId = -1;

    public String getLabel() {
        return label;
    }

    public SpanTestData setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getUri() {
        return uri;
    }

    public SpanTestData setUri(String uri) {
        this.uri = uri;
        return this;
    }

    public String getOperation() {
        return operation;
    }

    public SpanTestData setOperation(String operation) {
        this.operation = operation;
        return this;
    }

    public String getKind() {
        return kind;
    }

    public SpanTestData setKind(String kind) {
        this.kind = kind;
        return this;
    }

    public int getParentId() {
        return parentId;
    }

    public SpanTestData setParentId(int parentId) {
        this.parentId = parentId;
        return this;
    }

}
