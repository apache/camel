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
package org.apache.camel.component.platform.http;

import java.util.Locale;
import java.util.Objects;

import org.apache.camel.Consumer;
import org.apache.camel.util.StringHelper;

/**
 * Model of available http endpoints.
 */
public class HttpEndpointModel implements Comparable<HttpEndpointModel> {

    private final String uri;
    private String verbs;
    private final Consumer consumer;

    public HttpEndpointModel(String uri) {
        this(uri, null, null);
    }

    public HttpEndpointModel(String uri, String verbs) {
        this(uri, verbs, null);
    }

    public HttpEndpointModel(String uri, String verbs, Consumer consumer) {
        this.uri = uri;
        addVerb(verbs);
        this.consumer = consumer;
    }

    public String getUri() {
        return uri;
    }

    public String getVerbs() {
        return verbs;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void addVerb(String verb) {
        if (verb != null) {
            if (this.verbs == null) {
                this.verbs = "";
            }
            if (!StringHelper.containsIgnoreCase(this.verbs, verb)) {
                if (!this.verbs.isEmpty()) {
                    this.verbs += ",";
                }
                this.verbs += verb.toUpperCase(Locale.US);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        HttpEndpointModel that = (HttpEndpointModel) o;
        return uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public int compareTo(HttpEndpointModel o) {
        return uri.compareTo(o.uri);
    }
}
