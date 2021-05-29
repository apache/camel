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
package org.apache.camel.openapi.model;

import java.util.List;
import java.util.Map;

/**
 * Sample request POJO that uses Generics.
 */
public class GenericComplexRequestType<T extends GenericData> {

    private T data;
    private List<T> listOfData;
    private List<List<T>> listOfListOfData;
    private Map<String, T> mapOfData;
    private Map<String, Map<String, T>> mapOfMapOfData;

    public T getData() {
        return data;
    }

    public List<T> getListOfData() {
        return listOfData;
    }

    public Map<String, T> getMapOfData() {
        return mapOfData;
    }

    public List<List<T>> getListOfListOfData() {
        return listOfListOfData;
    }

    public Map<String, Map<String, T>> getMapOfMapOfData() {
        return mapOfMapOfData;
    }
}
