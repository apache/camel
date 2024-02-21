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
package org.apache.camel.wasm;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.camel.Exchange;

public final class WasmSupport {
    public static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private WasmSupport() {
    }

    public static byte[] serialize(Exchange exchange) throws Exception {
        Wrapper env = new Wrapper();
        env.body = exchange.getMessage().getBody(byte[].class);

        for (String headerName : exchange.getMessage().getHeaders().keySet()) {
            env.headers.put(headerName, exchange.getMessage().getHeader(headerName, String.class));
        }

        return MAPPER.writeValueAsBytes(env);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void deserialize(byte[] in, Exchange out) throws Exception {
        // cleanup
        out.getMessage().getHeaders().clear();
        out.getMessage().setBody(null);

        Wrapper w = MAPPER.readValue(in, Wrapper.class);
        out.getMessage().setBody(w.body);

        if (w.headers != null) {
            out.getMessage().setHeaders((Map) w.headers);
        }
    }

    public static class Wrapper {
        @JsonProperty
        public Map<String, String> headers = new HashMap<>();

        @JsonProperty
        public byte[] body;
    }
}
