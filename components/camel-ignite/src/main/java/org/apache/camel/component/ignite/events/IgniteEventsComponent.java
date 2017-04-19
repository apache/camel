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
package org.apache.camel.component.ignite.events;

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
 * The Ignite Events Component.
 */
public class IgniteEventsComponent extends AbstractIgniteComponent {

    public static IgniteEventsComponent fromIgnite(Ignite ignite) {
        IgniteEventsComponent answer = new IgniteEventsComponent();
        answer.setIgnite(ignite);
        return answer;
    }

    public static IgniteEventsComponent fromConfiguration(IgniteConfiguration configuration) {
        IgniteEventsComponent answer = new IgniteEventsComponent();
        answer.setIgniteConfiguration(configuration);
        return answer;
    }

    public static IgniteEventsComponent fromInputStream(InputStream inputStream) {
        IgniteEventsComponent answer = new IgniteEventsComponent();
        answer.setConfigurationResource(inputStream);
        return answer;
    }

    public static IgniteEventsComponent fromUrl(URL url) {
        IgniteEventsComponent answer = new IgniteEventsComponent();
        answer.setConfigurationResource(url);
        return answer;
    }

    public static IgniteEventsComponent fromLocation(String location) {
        IgniteEventsComponent answer = new IgniteEventsComponent();
        answer.setConfigurationResource(location);
        return answer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        IgniteEventsEndpoint answer = new IgniteEventsEndpoint(uri, remaining, parameters, this);
        setProperties(answer, parameters);
        return answer;
    }

}
