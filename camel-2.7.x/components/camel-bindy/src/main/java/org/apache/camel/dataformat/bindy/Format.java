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
package org.apache.camel.dataformat.bindy;

/**
 * Format allows to format object to and from string received using format or
 * parse method
 */
public interface Format<T> {

    /**
     * Formats the object into a String
     * 
     * @param object the object
     * @return formatted as a String
     * @throws Exception can be thrown
     */
    String format(T object) throws Exception;

    /**
     * Parses a String into an object
     * 
     * @param string the string
     * @return T the object
     * @throws Exception can be thrown
     */
    T parse(String string) throws Exception;

}
