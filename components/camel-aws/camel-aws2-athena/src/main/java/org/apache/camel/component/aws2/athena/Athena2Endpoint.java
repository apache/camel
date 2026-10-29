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
package org.apache.camel.component.aws2.athena;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.athena.client.Athena2ClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.athena.AthenaClient;

/**
 * Access AWS Athena.
 */
@UriEndpoint(firstVersion = "3.4.0", scheme = "aws2-athena", title = "AWS Athena", syntax = "aws2-athena:label",
             producerOnly = true, category = { Category.CLOUD, Category.DATABASE }, headersClass = Athena2Constants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "operation", value = "startQueryExecution", carriers = {
                                          Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.NULL }),
                                  description = "The SQL of startQueryExecution, the default, as text: bytes, a stream or a"
                                                + " file are read as text, a Map or a POJO is sent as its toString(). With no"
                                                + " body the SQL comes from the CamelAwsAthenaQueryString header or the"
                                                + " queryString option. The other operations do not use the body: they take the"
                                                + " id of the query from the CamelAwsAthenaQueryExecutionId header, which"
                                                + " startQueryExecution sets"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "startQueryExecution",
                                                          carriers = { Carrier.NULL, Carrier.POJO }),
                                           @BodyType.When(option = "operation", value = "getQueryResults",
                                                          carriers = { Carrier.ITERATOR, Carrier.POJO, Carrier.TEXT }) },
                                   description = "startQueryExecution, the default, leaves no body, with the id of the query in"
                                                 + " the CamelAwsAthenaQueryExecutionId header; with waitTimeout, the"
                                                 + " GetQueryExecutionResponse of the AWS SDK of the last check, whatever the"
                                                 + " state of the query. getQueryResults gives by outputType: for StreamList,"
                                                 + " the default, a GetQueryResultsIterable of the pages of rows, which calls"
                                                 + " Athena when it is read, such as by a split; for SelectList one page as a"
                                                 + " GetQueryResultsResponse; for S3Pointer the S3 location of the results as"
                                                 + " text. getQueryExecution and listQueryExecutions give the response of the"
                                                 + " AWS SDK"))
public class Athena2Endpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private AthenaClient athenaClient;

    @UriParam
    private Athena2Configuration configuration;

    public Athena2Endpoint(String uri, Component component, Athena2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Athena2Producer(this);
    }

    @Override
    public Athena2Component getComponent() {
        return (Athena2Component) super.getComponent();
    }

    @Override
    public void doInit() throws Exception {
        super.doInit();

        athenaClient = ObjectHelper.isNotEmpty(configuration.getAmazonAthenaClient())
                ? configuration.getAmazonAthenaClient()
                : Athena2ClientFactory.getAthenaClient(configuration);

    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getAmazonAthenaClient())) {
            if (ObjectHelper.isNotEmpty(athenaClient)) {
                athenaClient.close();
            }
        }
        super.doStop();
    }

    public Athena2Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Athena2Configuration configuration) {
        this.configuration = configuration;
    }

    public AthenaClient getAthenaClient() {
        return athenaClient;
    }

    public void setAthenaClient(AthenaClient athenaClient) {
        this.athenaClient = athenaClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            return configuration.getRegion();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "athena";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (ObjectHelper.isNotEmpty(configuration.getDatabase())) {
            return Map.of("database", configuration.getDatabase());
        }
        return null;
    }
}
