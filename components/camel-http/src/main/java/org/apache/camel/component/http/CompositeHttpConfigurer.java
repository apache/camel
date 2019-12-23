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
package org.apache.camel.component.http;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.impl.client.HttpClientBuilder;

public class CompositeHttpConfigurer implements HttpClientConfigurer {

    private final List<HttpClientConfigurer> configurers = new ArrayList<>();

    public void addConfigurer(HttpClientConfigurer configurer) {
        if (configurer != null) {
            configurers.add(configurer);
        }
    }

    public void removeConfigurer(HttpClientConfigurer configurer) {
        configurers.remove(configurer);
    }

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        for (HttpClientConfigurer configurer : configurers) {
            configurer.configureHttpClient(clientBuilder);
        }
    }

    public static CompositeHttpConfigurer combineConfigurers(HttpClientConfigurer oldConfigurer, HttpClientConfigurer newConfigurer) {
        if (oldConfigurer instanceof CompositeHttpConfigurer) {
            ((CompositeHttpConfigurer) oldConfigurer).addConfigurer(newConfigurer);
            return (CompositeHttpConfigurer) oldConfigurer;
        } else {
            CompositeHttpConfigurer answer = new CompositeHttpConfigurer();
            answer.addConfigurer(newConfigurer);
            answer.addConfigurer(oldConfigurer);
            return answer;
        }
    }

}
