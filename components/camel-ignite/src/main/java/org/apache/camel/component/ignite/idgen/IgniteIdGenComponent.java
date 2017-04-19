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
package org.apache.camel.component.ignite.idgen;

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
 * The Ignite ID Generator Component.
 */
public class IgniteIdGenComponent extends AbstractIgniteComponent {

    public static IgniteIdGenComponent fromIgnite(Ignite ignite) {
        IgniteIdGenComponent answer = new IgniteIdGenComponent();
        answer.setIgnite(ignite);
        return answer;
    }

    public static IgniteIdGenComponent fromConfiguration(IgniteConfiguration configuration) {
        IgniteIdGenComponent answer = new IgniteIdGenComponent();
        answer.setIgniteConfiguration(configuration);
        return answer;
    }

    public static IgniteIdGenComponent fromInputStream(InputStream inputStream) {
        IgniteIdGenComponent answer = new IgniteIdGenComponent();
        answer.setConfigurationResource(inputStream);
        return answer;
    }

    public static IgniteIdGenComponent fromUrl(URL url) {
        IgniteIdGenComponent answer = new IgniteIdGenComponent();
        answer.setConfigurationResource(url);
        return answer;
    }

    public static IgniteIdGenComponent fromLocation(String location) {
        IgniteIdGenComponent answer = new IgniteIdGenComponent();
        answer.setConfigurationResource(location);
        return answer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "Camel Context");
        IgniteIdGenEndpoint answer = new IgniteIdGenEndpoint(uri, remaining, parameters, this);
        setProperties(answer, parameters);
        return answer;
    }

}
