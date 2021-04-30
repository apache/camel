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
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SampleComplexRequestType extends GenericComplexRequestType<CustomData> {
    @JsonProperty(required = true)
    private String requestField1;
    private String requestField2;
    private List<String> listOfStrings;
    private String[] arrayOfString;
    private Map<String, String> mapOfStrings;
    private TimeUnit timeUnit;
    private InnerClass innerClass;

    public String getRequestField1() {
        return requestField1;
    }

    public String getRequestField2() {
        return requestField2;
    }

    public List<String> getListOfStrings() {
        return listOfStrings;
    }

    public String[] getArrayOfString() {
        return arrayOfString;
    }

    @JsonProperty(required = true)
    public Map<String, String> getMapOfStrings() {
        return mapOfStrings;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public InnerClass getInnerClass() {
        return innerClass;
    }

    public static class InnerClass {
        private long longField;

        public long getLongField() {
            return longField;
        }
    }
}
