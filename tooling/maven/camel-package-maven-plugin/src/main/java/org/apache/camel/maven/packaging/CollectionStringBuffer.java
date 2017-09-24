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
package org.apache.camel.maven.packaging;

/**
 * A little helper class for converting a collection of values to a (usually comma separated) string.
 */
public class CollectionStringBuffer {

    private final StringBuilder buffer = new StringBuilder();
    private String separator;
    private boolean first = true;

    public CollectionStringBuffer() {
        this(", ");
    }

    public CollectionStringBuffer(String separator) {
        this.separator = separator;
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    public void append(Object value) {
        if (first) {
            first = false;
        } else {
            buffer.append(separator);
        }
        buffer.append(value);
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

}
