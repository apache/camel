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
package org.apache.camel.component.spring.ws;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Access external web services as a client or expose your own web services.
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "spring-ws", title = "Spring WebService",
             syntax = "spring-ws:type:lookupKey:webServiceEndpointUri",
             category = { Category.WEBSERVICE }, headersClass = SpringWebserviceConstants.class)
@Metadata(annotations = {
        "protocol=http",
})
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, format = "xml",
                                   description = "The payload of the SOAP body as a javax.xml.transform.Source (a DOMSource with"
                                                 + " the default SAAJ message factory); convertBodyTo String gives the XML."
                                                 + " The SOAP header is in the CamelSpringWebserviceSoapHeader header"),
           consumerReply = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.POJO }, format = "xml",
                                     description = "The response payload as XML: a String, bytes, a stream cache, a Source or a"
                                                   + " DOM Document. A body that does not convert to a Source, such as a Map, gives"
                                                   + " an empty SOAP body without an error"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.POJO }, format = "xml",
                                  description = "The request payload as XML: a String, bytes, a stream cache, a Source or a DOM"
                                                + " Document. No body, a File, a Map or a number fails: read or marshal it to"
                                                + " XML first"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.NULL }, format = "xml",
                                   description = "The payload of the SOAP response as a javax.xml.transform.Source (a DOMSource"
                                                 + " with the default SAAJ message factory), null for an empty SOAP body; also with"
                                                 + " the InOnly pattern"))
public class SpringWebserviceEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    @UriParam
    private SpringWebserviceConfiguration configuration;

    public SpringWebserviceEndpoint(Component component, String uri, SpringWebserviceConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SpringWebserviceConsumer consumer = new SpringWebserviceConsumer(this, processor);
        if (configuration.getEndpointDispatcher() != null) {
            configuration.getEndpointDispatcher().setConsumerMessageEndpoint(consumer);
        }
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringWebserviceProducer(this);
    }

    public SpringWebserviceConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return configuration.getHeaderFilterStrategy();
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        configuration.setHeaderFilterStrategy(headerFilterStrategy);
    }

}
