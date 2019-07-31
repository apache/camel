/*
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
package org.apache.camel.component.hbase;

public enum HBaseAttribute {

    HBASE_ROW_ID("CamelHBaseRowId"),
    HBASE_ROW_TYPE("CamelHBaseRowType"),
    HBASE_MARKED_ROW_ID("CamelHBaseMarkedRowId"),
    HBASE_FAMILY("CamelHBaseFamily"),
    HBASE_QUALIFIER("CamelHBaseQualifier"),
    HBASE_VALUE("CamelHBaseValue"),
    HBASE_VALUE_TYPE("CamelHBaseValueType");

    private final String value;
    private final String option;

    HBaseAttribute(String value) {
        this.value = value;
        this.option = asOption(value);
    }

    public String asHeader(int i) {
        if (i > 1) {
            return value + i;
        } else {
            return value;
        }
    }

    public String asHeader() {
        return value;
    }

    public String asOption() {
        return option;
    }

    public String asOption(int i) {
        if (i > 1) {
            return option + i;
        } else {
            return option;
        }
    }

    @Override
    public String toString() {
        return value;
    }

    private static String asOption(String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(name, "CamelHBase".length(), name.length());
        sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));

        return sb.toString();
    }
}
