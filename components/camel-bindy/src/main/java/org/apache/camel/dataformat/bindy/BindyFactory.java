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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;

/**
 * The bindy factory is a factory used to create the POJO models and bind or
 * unbind the data to and from the record (CSV, ...)
 */
public interface BindyFactory {

    /**
     * Prior to bind or unbind the data to and from string or model classes, the
     * factory must create a collection of objects representing the model
     * 
     * @throws Exception can be thrown
     */
    void initModel() throws Exception;

    /**
     * The bind allow to read the content of a record (expressed as a
     * List<String>) and map it to the model classes.
     * 
     * @param data List<String> represents the csv, ... data to transform
     * @param model Map<String, object> is a collection of objects used to bind
     *            data. String is the the key name of the class link to POJO
     *            objects
     * @param line is the position of the record into the file
     * @throws Exception can be thrown
     */
    void bind(CamelContext camelContext, List<String> data, Map<String, Object> model, int line) throws Exception;

    /**
     * The unbind is used to transform the content of the classes model objects
     * into a string. The string represents a record of a CSV file
     * 
     * @return String represents a csv record created
     * @param model Map<String, Object> is a collection of objects used to
     *            create csv, ... records. String is the the key name of the
     *            class link to POJO objects
     * @throws Exception can be thrown
     */
    String unbind(CamelContext camelContext, Map<String, Object> model) throws Exception;

}
