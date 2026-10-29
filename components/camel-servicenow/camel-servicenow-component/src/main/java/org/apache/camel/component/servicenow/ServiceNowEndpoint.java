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
package org.apache.camel.component.servicenow;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interact with <a href="http://www.servicenow.com/">ServiceNow</a> via its REST API.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "servicenow", title = "ServiceNow", syntax = "servicenow:instanceName",
             producerOnly = true, category = { Category.API, Category.CLOUD, Category.MANAGEMENT },
             headersClass = ServiceNowConstants.class)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.MAP, Carrier.POJO, Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.NULL },
                                  description = "The record to create, modify or update must be an instance of the request"
                                                + " model: a Map by default, an object of the class set with models or"
                                                + " requestModels or the CamelServiceNowRequestModel header, or JSON text"
                                                + " with a String model; another body fails. An attachment upload sends the"
                                                + " body as a stream, with its content type in the CamelServiceNowContentType"
                                                + " header. A retrieve or delete does not read the body: the action, table"
                                                + " and sys_id are headers"),
           producerOut = @BodyType(value = {
                   Carrier.MAP, Carrier.LIST, Carrier.POJO, Carrier.TEXT, Carrier.STREAM, Carrier.UNCHANGED },
                                   description = "The result of the call: a Map of the record, or a List of Maps for many,"
                                                 + " or objects of the response model class set with models or"
                                                 + " responseModels or the CamelServiceNowResponseModel header (the JSON"
                                                 + " text with a String model). The content of an attachment is an"
                                                 + " InputStream. A response with no content, such as a delete, leaves the"
                                                 + " body; the paging offsets and the other response fields are headers"))
public class ServiceNowEndpoint extends DefaultEndpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceNowEndpoint.class);

    @UriPath(description = "The ServiceNow instance name")
    @Metadata(required = true)
    private final String instanceName;

    @UriParam
    private final ServiceNowConfiguration configuration;

    public ServiceNowEndpoint(String uri, ServiceNowComponent component, ServiceNowConfiguration configuration,
                              String instanceName) {
        super(uri, component);

        this.configuration = configuration;
        this.instanceName = instanceName;
    }

    @Override
    public Producer createProducer() throws Exception {
        ServiceNowProducer producer = configuration.getRelease().get(this);
        LOGGER.info("Producer for ServiceNow Rel. {} = {}/{}",
                configuration.getRelease().name(),
                producer.getRelease().name(),
                producer.getClass().getName());

        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported");
    }

    public ServiceNowConfiguration getConfiguration() {
        return configuration;
    }

    public String getInstanceName() {
        return instanceName;
    }
}
