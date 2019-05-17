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
package org.apache.camel.service.lra;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Endpoint;

import static org.apache.camel.service.lra.LRAConstants.URL_COMPENSATION_KEY;
import static org.apache.camel.service.lra.LRAConstants.URL_COMPLETION_KEY;

public class LRAUrlBuilder {

    private String host;

    private String path = "";

    private String query = "";

    public LRAUrlBuilder() {
    }

    public LRAUrlBuilder(String host, String path, String query) {
        this.host = host;
        this.path = path;
        this.query = query;
    }

    public LRAUrlBuilder host(String host) {
        if (this.host != null) {
            throw new IllegalStateException("Host already set");
        }
        LRAUrlBuilder copy = copy();
        copy.host = host;
        return copy;
    }

    public LRAUrlBuilder path(String path) {
        LRAUrlBuilder copy = copy();
        copy.path = joinPath(this.path, path);
        return copy;
    }

    public LRAUrlBuilder compensation(Optional<Endpoint> endpoint) {
        if (endpoint.isPresent()) {
            return compensation(endpoint.get().getEndpointUri());
        }
        return this;
    }

    public LRAUrlBuilder compensation(String uri) {
        return this.query(URL_COMPENSATION_KEY, uri);
    }

    public LRAUrlBuilder completion(Optional<Endpoint> endpoint) {
        if (endpoint.isPresent()) {
            return completion(endpoint.get().getEndpointUri());
        }
        return this;
    }

    public LRAUrlBuilder completion(String uri) {
        return this.query(URL_COMPLETION_KEY, uri);
    }

    public LRAUrlBuilder options(Map<String, ?> options) {
        LRAUrlBuilder result = this;
        for (Map.Entry<String, ?> entry : options.entrySet()) {
            result = result.query(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public LRAUrlBuilder query(String key, Object value) {
        LRAUrlBuilder copy = copy();
        try {
            key = URLEncoder.encode(toNonnullString(key), StandardCharsets.UTF_8.name());
            value = URLEncoder.encode(toNonnullString(value), StandardCharsets.UTF_8.name());
            if (copy.query.length() == 0) {
                copy.query += "?";
            } else {
                copy.query += "&";
            }
            copy.query += key + "=" + value;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return copy;
    }

    public String build() {
        if (this.host == null) {
            throw new IllegalStateException("Host not set");
        }
        return joinPath(this.host, this.path) + query;
    }

    private String joinPath(String first, String second) {
        first = toNonnullString(first);
        second = toNonnullString(second);
        while (first.endsWith("/")) {
            first = first.substring(0, first.length() - 1);
        }
        while (second.startsWith("/")) {
            second = second.substring(1);
        }
        return first + "/" + second;
    }

    private String toNonnullString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private LRAUrlBuilder copy() {
        return new LRAUrlBuilder(this.host, this.path, this.query);
    }

}
