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

package org.apache.camel.component.avro;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;
import org.apache.avro.Protocol;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.URISupport;

public class AvroComponent extends DefaultComponent {

    private AvroConfiguration configuration;

    public AvroComponent() {
    }

    public AvroComponent(CamelContext context) {
        super(context);
    }


    /**
     * A factory method allowing derived components to create a new endpoint
     * from the given URI, remaining path and optional parameters
     *
     * @param uri        the full URI of the endpoint
     * @param remaining  the remaining part of the URI without the query
     *                   parameters or component prefix
     * @param parameters the optional parameters passed in
     * @return a newly created endpoint or null if the endpoint cannot be
     *         created based on the inputs
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        AvroConfiguration config;
        if (configuration != null) {
            config = configuration.copy();
        } else {
            config = new AvroConfiguration();
        }
        URI enpointUri = new URI(URISupport.normalizeUri(remaining));
        config.parseURI(enpointUri, parameters, this);
        setProperties(config, parameters);

        if (config.getProtocol() == null && config.getProtocolClassName() != null) {
            Class<?> protocolClass = getCamelContext().getClassResolver().resolveClass(config.getProtocolClassName());
            Field f = protocolClass.getField("PROTOCOL");
            Protocol protocol = (Protocol) f.get(null);
            config.setProtocol(protocol);
        }

        if (AvroConstants.AVRO_NETTY_TRANSPORT.equals(enpointUri.getScheme())) {
            return new AvroNettyEndpoint(remaining, this, config);
        } else if (AvroConstants.AVRO_HTTP_TRANSPORT.equals(enpointUri.getScheme())) {
            return new AvroHttpEndpoint(remaining, this, config);
        } else {
            throw new IllegalArgumentException("Unknown avro scheme. Should use either netty or http.");
        }
    }


    public AvroConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(AvroConfiguration configuration) {
        this.configuration = configuration;
    }
}
