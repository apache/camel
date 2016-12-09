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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

/**
 * Contains the individual result of Composite API batch request. As batch requests can partially succeed or fail make
 * sure you check the {@link #getStatusCode()} for status of the specific request. The result of the request can vary
 * from API to API so here it is given as {@link Object}, in most cases it will be a {@link Map} with string keys and
 * values or other {@link Map} as value. Requests made in JSON format hold some type information (i.e. it is known what
 * values are strings and what values are numbers), so in general those will be more type friendly. Note that the
 * responses will vary between XML and JSON, this is due to the responses from Salesforce API being different.
 * <p>
 * For example response for SObject record creation in JSON will be: <blockquote>
 *
 * <pre>
 * {
 *   "statusCode": 201,
 *   "result": {
 *     "id" : "0010Y00000Ary8hQAB",
 *     "success" : true,
 *     "errors" : []
 *   }
 * }
 * </pre>
 *
 * </blockquote>
 * <p>
 * Which will result in {@link #getResult()} returning {@link Map} created like: <blockquote>
 *
 * <pre>
 * {@code
 * Map<String, Object> result = new HashMap<>();
 * result.put("id", "0010Y00000Ary91QAB");
 * result.put("success", Boolean.TRUE);
 * result.put("errors", Collections.emptyList());
 * }
 * </pre>
 *
 * </blockquote>
 * <p>
 * Whereas using XML format the response will be: <blockquote>
 *
 * <pre>
 * {@code
 * <Result>
 *   <id>0010Y00000AryACQAZ</id>
 *   <success>true</success>
 * </Result>
 * }
 * </pre>
 *
 * </blockquote>
 * <p>
 * And that results in {@link #getResult()} returning {@link Map} created like: <blockquote>
 *
 * <pre>
 * {@code
 * Map<String, Object> result = new HashMap<>();
 *
 * Map<String, Object> nestedResult = new HashMap<>();
 * result.put("Result", nestedResult);
 *
 * nestedResult.put("id", "0010Y00000Ary91QAB");
 * nestedResult.put("success", "true");
 * }
 * </pre>
 *
 * </blockquote>
 * <p>
 * Note the differences between type and nested {@link Map} one level deeper in the case of XML.
 */
@XStreamAlias("batchResult")
public final class SObjectBatchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @XStreamConverter(MapOfMapsConverter.class)
    private final Object result;

    private final int statusCode;

    @JsonCreator
    public SObjectBatchResult(@JsonProperty("statusCode") final int statusCode,
            @JsonProperty("result") final Object result) {
        this.statusCode = statusCode;
        this.result = result;
    }

    public Object getResult() {
        return result;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return "<statusCode: " + statusCode + ", result: " + result + ">";
    }
}
