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
package org.apache.camel.component.connector;

/**
 * Data types supported by Camel connectors.
 * <p/>
 * A connector is more strict that a regular Camel component and as such the connector
 * is limited to supporting one data type as input and output.
 */
public final class DataType {

    /**
     * The supported data types.
     */
    public enum Type {
        none, any, java, text, xml, json;
    }

    private final Type type;
    private final String subType;

    DataType(String text) {
        String[] parts = text.split(":");

        String name = parts[0].toLowerCase();
        // allow * as shorthand for any kind
        if ("*".equals(name)) {
            name = "any";
        }

        type = Type.valueOf(name);
        if (parts.length == 2) {
            subType = parts[1];
        } else {
            subType = null;
        }
    }

    DataType(Type type, String subType) {
        this.type = type;
        this.subType = subType;
    }

    /**
     * The type one of <tt>none</tt>, <tt>any</tt> (you can also use <tt>*</tt> as any), <tt>java</tt>, <tt>text</tt>, <tt>xml</tt>, or <tt>json</tt>.
     */
    public Type getType() {
        return type;
    }

    /**
     * Optional sub type to qualify the data type such as a java fully qualified class name, or a xml namespace etc
     */
    public String getSubType() {
        return subType;
    }

    @Override
    public String toString() {
        if (subType != null) {
            return type.name() + ":" + subType;
        } else {
            return type.name();
        }
    }
}
