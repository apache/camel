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
package org.apache.camel.component.ignite.queue;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.ignite.AbstractIgniteComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Ignite Queue Component.
 */
public class IgniteQueueComponent extends AbstractIgniteComponent {

    public static IgniteQueueComponent fromIgnite(Ignite ignite) {
        IgniteQueueComponent answer = new IgniteQueueComponent();
        answer.setIgnite(ignite);
        return answer;
    }

    public static IgniteQueueComponent fromConfiguration(IgniteConfiguration configuration) {
        IgniteQueueComponent answer = new IgniteQueueComponent();
        answer.setIgniteConfiguration(configuration);
        return answer;
    }

    public static IgniteQueueComponent fromInputStream(InputStream inputStream) {
        IgniteQueueComponent answer = new IgniteQueueComponent();
        answer.setConfigurationResource(inputStream);
        return answer;
    }

    public static IgniteQueueComponent fromUrl(URL url) {
        IgniteQueueComponent answer = new IgniteQueueComponent();
        answer.setConfigurationResource(url);
        return answer;
    }

    public static IgniteQueueComponent fromLocation(String location) {
        IgniteQueueComponent answer = new IgniteQueueComponent();
        answer.setConfigurationResource(location);
        return answer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        IgniteQueueEndpoint answer = new IgniteQueueEndpoint(uri, remaining, parameters, this);
        setProperties(answer, parameters);
        return answer;
    }

}
