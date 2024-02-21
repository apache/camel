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
package org.apache.camel;

/**
 * An entity that can point to a given line number from a source {@link org.apache.camel.spi.Resource} such as YAML and
 * XML DSL parsers.
 */
public interface LineNumberAware {

    /**
     * The line number of this entity.
     *
     * @return -1 if line number is not possible to know
     */
    int getLineNumber();

    /**
     * Sets the line number of this entity. parsing the source file and provide the line number representing this node.
     *
     * @param lineNumber the line number
     */
    void setLineNumber(int lineNumber);

    /**
     * The location of the entity.
     */
    String getLocation();

    /**
     * Sets the location of the entity (source file name, i.e. foo.java, bar.xml, etc.)
     */
    void setLocation(String location);

    /**
     * Set the {@link LineNumberAware} if the object is an instance of {@link LineNumberAware}.
     */
    static <T> T trySetLineNumberAware(T object, LineNumberAware source) {
        if (source != null && object instanceof LineNumberAware) {
            ((LineNumberAware) object).setLineNumber(source.getLineNumber());
            ((LineNumberAware) object).setLocation(source.getLocation());
        }
        return object;
    }

}
