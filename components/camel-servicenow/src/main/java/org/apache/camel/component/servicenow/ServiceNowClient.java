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
package org.apache.camel.component.servicenow;

import java.util.Arrays;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.camel.CamelException;
import org.apache.camel.Message;
import org.apache.camel.component.servicenow.auth.AuthenticationRequestFilter;
import org.apache.cxf.jaxrs.client.WebClient;

public class ServiceNowClient {
    private final ServiceNowConfiguration configuration;
    private final WebClient client;

    public ServiceNowClient(ServiceNowConfiguration configuration) throws Exception {
        this.configuration = configuration;
        this.client = WebClient.create(
            configuration.getApiUrl(),
            Arrays.asList(
                new AuthenticationRequestFilter(configuration),
                new JacksonJsonProvider(configuration.getMapper()),
                new ServiceNowExceptionMapper(configuration.getMapper())
            ),
            true
        );

        WebClient.getConfig(client)
            .getRequestContext()
            .put("org.apache.cxf.http.header.split", true);
    }

    public ServiceNowClient types(MediaType type) {
        return types(type, type);
    }

    public ServiceNowClient types(MediaType accept, MediaType type) {
        client.accept(accept);
        client.type(type);
        return this;
    }

    public ServiceNowClient path(Object path) {
        if (path != null) {
            client.path(path);
        }

        return this;
    }

    public ServiceNowClient type(MediaType ct) {
        client.type(ct);
        return this;
    }

    public ServiceNowClient type(String type) {
        client.type(type);
        return this;
    }

    public ServiceNowClient accept(MediaType... types) {
        client.accept(types);
        return this;
    }

    public ServiceNowClient accept(String... types) {
        client.accept(types);
        return this;
    }

    public ServiceNowClient query(String name, Object... values) {
        client.query(name, values);
        return this;
    }

    public ServiceNowClient query(ServiceNowParam param, Message message) {
        Object value = param.getHeaderValue(message, configuration);
        if (value != null) {
            client.query(param.getId(), value);
        }

        return this;
    }

    public Response invoke(String httpMethod) throws Exception {
        return invoke(httpMethod, null);
    }

    public Response invoke(String httpMethod, Object body) throws Exception {
        Response response = client.invoke(httpMethod, body);
        int code = response.getStatus();

        // Only ServiceNow known error status codes are mapped
        // See http://wiki.servicenow.com/index.php?title=REST_API#REST_Response_HTTP_Status_Codes
        switch(code) {
        case 200:
        case 201:
        case 204:
            // Success
            break;
        case 400:
        case 401:
        case 403:
        case 404:
        case 405:
        case 406:
        case 415:
            throw response.readEntity(ServiceNowException.class);
        default:
            throw new CamelException(
                String.format("Status (%d): %s", code, response.readEntity(Map.class))
            );
        }

        return response;
    }

    public ServiceNowClient reset() {
        client.back(true);
        client.reset();
        client.resetQuery();

        return this;
    }
}
