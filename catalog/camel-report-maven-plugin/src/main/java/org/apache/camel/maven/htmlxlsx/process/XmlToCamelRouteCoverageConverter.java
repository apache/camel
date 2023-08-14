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
package org.apache.camel.maven.htmlxlsx.process;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.camel.maven.htmlxlsx.model.TestResult;

public class XmlToCamelRouteCoverageConverter {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private final XmlMapper xmlMapper = new XmlMapper();

    public TestResult convert(String source) {

        Map<String, Object> map;

        try {
            map = xmlMapper.readValue(source, Map.class);

            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);

            return readValue(json);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected TestResult readValue(String json) throws JsonProcessingException {

        return objectMapper.readValue(json, TestResult.class);
    }
}
