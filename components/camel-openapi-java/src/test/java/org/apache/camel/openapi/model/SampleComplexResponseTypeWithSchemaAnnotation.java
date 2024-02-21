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

import java.time.Month;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "sampleResponseWithSchema")
public class SampleComplexResponseTypeWithSchemaAnnotation {
    @JsonProperty(required = true)
    private String responseField1 = "Response Field 1";
    private String responseField2 = "Response Field 2";
    private String[] arrayOfStrings;
    private Month month;
    private InnerClass innerClass;

    public String getResponseField1() {
        return responseField1;
    }

    public String getResponseField2() {
        return responseField2;
    }

    @JsonProperty(required = true)
    public String[] getArrayOfStrings() {
        return arrayOfStrings;
    }

    public Month getMonth() {
        return month;
    }

    public InnerClass getInnerClass() {
        return innerClass;
    }

    @Schema(name = "responseInner")
    public static class InnerClass {
        double doubleField;

        public double getDoubleField() {
            return doubleField;
        }
    }
}
