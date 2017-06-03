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
package org.apache.camel.parser;

/**
 * Result of parsing Camel RouteBuilder or XML routes from the source code.
 */
public class ParserResult {

    private final String node;
    private boolean parsed;
    private int position;
    private String element;
    private Boolean predicate;

    public ParserResult(String node, int position, String element) {
        this(node, position, element, true);
    }

    public ParserResult(String node, int position, String element, boolean parsed) {
        this.node = node;
        this.position = position;
        this.element = element;
        this.parsed = parsed;
    }

    /**
     * Character based position in the source code (not line based).
     */
    public int getPosition() {
        return position;
    }

    /**
     * The element such as a Camel endpoint uri
     */
    public String getElement() {
        return element;
    }

    /**
     * Whether the element was successfully parsed. If the parser cannot parse
     * the element for whatever reason this will return <tt>false</tt>.
     */
    public boolean isParsed() {
        return parsed;
    }

    /**
     * The node which is typically a Camel EIP name such as <tt>to</tt>, <tt>wireTap</tt> etc.
     */
    public String getNode() {
        return node;
    }

    public Boolean getPredicate() {
        return predicate;
    }

    /**
     * Tells if it was an expression which is intended to be used as a predicate (determined from camel-core mode)
     */
    public void setPredicate(Boolean predicate) {
        this.predicate = predicate;
    }

    public String toString() {
        return element;
    }
}
