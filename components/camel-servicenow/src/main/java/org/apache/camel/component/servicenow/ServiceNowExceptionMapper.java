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

import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.client.ResponseExceptionMapper;

@Provider
@Priority(Priorities.USER)
public class ServiceNowExceptionMapper implements ResponseExceptionMapper<ServiceNowException> {
    private final ObjectMapper mapper;

    public ServiceNowExceptionMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ServiceNowException fromResponse(Response r) {
        int code = r.getStatus();

        try {
            // Only ServiceNow known error status codes are mapped
            // See http://wiki.servicenow.com/index.php?title=REST_API#REST_Response_HTTP_Status_Codes
            if (code == Response.Status.NOT_FOUND.getStatusCode()
                || code == Response.Status.BAD_REQUEST.getStatusCode()
                || code == Response.Status.UNAUTHORIZED.getStatusCode()
                || code == Response.Status.FORBIDDEN.getStatusCode()
                || code == Response.Status.METHOD_NOT_ALLOWED.getStatusCode()) {

                return mapper.readValue((InputStream)r.getEntity(), ServiceNowException.class);
            }
        } catch (IOException e) {
            return new ServiceNowException(e);
        }

        return null;
    }
}
