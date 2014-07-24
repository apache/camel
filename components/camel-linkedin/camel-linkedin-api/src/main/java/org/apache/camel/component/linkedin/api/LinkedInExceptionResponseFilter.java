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
package org.apache.camel.component.linkedin.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.component.linkedin.api.model.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Response filter for throwing {@link LinkedInException}
 * when response contains {@link org.apache.camel.component.linkedin.api.model.Error}
 */
@Provider
@Priority(Priorities.USER)
public class LinkedInExceptionResponseFilter implements ClientResponseFilter {

    private static final Logger LOG = LoggerFactory.getLogger(LinkedInExceptionResponseFilter.class);
    private final JAXBContext jaxbContext;

    public LinkedInExceptionResponseFilter() {
        try {
            jaxbContext = JAXBContext.newInstance(Error.class.getPackage().getName());
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Error initializing JAXB: " + e.getMessage(), e);
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        if (responseContext.getStatus() != Response.Status.OK.getStatusCode() && responseContext.hasEntity()) {
            try {
                final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                final Error error = (Error) unmarshaller.unmarshal(responseContext.getEntityStream());

                final Response.ResponseBuilder builder = Response.status(responseContext.getStatusInfo());
                builder.entity(error);
                // copy response headers
                for (Map.Entry<String, List<String>> header : responseContext.getHeaders().entrySet()) {
                    builder.header(header.getKey(), header.getValue());
                }

                throw new LinkedInException(error, builder.build());
            } catch (JAXBException e) {
                // log and ignore
                LOG.warn("Unable to parse LinkedIn error: " + e.getMessage(), e);
            }
        }
    }
}
