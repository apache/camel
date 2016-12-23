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
package org.apache.camel.jsonpath.jackson;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.camel.Exchange;
import org.apache.camel.jsonpath.JsonPathAdapter;
import org.apache.camel.spi.Registry;

/**
 * A Jackson {@link JsonPathAdapter} which is using Jackson to convert the message
 * body to {@link Map}. This allows us to support POJO classes with camel-jsonpath.
 */
public class JacksonJsonAdapter implements JsonPathAdapter {

    private final ObjectMapper defaultMapper;

    public JacksonJsonAdapter() {
        defaultMapper = new ObjectMapper();
        // Enables JAXB processing so we can easily convert JAXB annotated pojos also
        JaxbAnnotationModule module = new JaxbAnnotationModule();
        defaultMapper.registerModule(module);
    }

    @Override
    public Map readValue(Object body, Exchange exchange) {
        ObjectMapper mapper = resolveObjectMapper(exchange.getContext().getRegistry());
        try {
            return mapper.convertValue(body, Map.class);
        } catch (Throwable e) {
            // ignore because we are attempting to convert
        }

        return null;
    }

    private ObjectMapper resolveObjectMapper(Registry registry) {
        Set<ObjectMapper> mappers = registry.findByType(ObjectMapper.class);
        if (mappers.size() == 1) {
            return mappers.iterator().next();
        }
        return defaultMapper;
    }

}

