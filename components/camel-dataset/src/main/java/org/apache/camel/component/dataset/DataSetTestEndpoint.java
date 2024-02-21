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
package org.apache.camel.component.dataset;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extends the mock component by pulling messages from another endpoint on startup to set the expected message bodies.
 *
 * That is, you use the test endpoint in a route and messages arriving on it will be implicitly compared to some
 * expected messages extracted from some other location. So you can use, for example, an expected set of message bodies
 * as files. This will then set up a properly configured Mock endpoint, which is only valid if the received messages
 * match the number of expected messages and their message payloads are equal.
 */
@UriEndpoint(firstVersion = "1.3.0", scheme = "dataset-test", title = "DataSet Test", syntax = "dataset-test:name",
             remote = false, producerOnly = true, category = { Category.CORE, Category.TESTING }, lenientProperties = true)
public class DataSetTestEndpoint extends MockEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(DataSetTestEndpoint.class);

    private Endpoint expectedMessageEndpoint;

    @UriPath(description = "Name of endpoint to lookup in the registry to use for polling messages used for testing")
    @Metadata(required = true)
    private String name;
    @UriParam
    private boolean anyOrder;
    @UriParam(defaultValue = "2000", javaType = "java.time.Duration")
    private long timeout = 2000L;
    @UriParam
    private boolean split;
    @UriParam
    private String delimiter = "\\n|\\r";

    public DataSetTestEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public void setExpectedMessageEndpoint(Endpoint expectedMessageEndpoint) {
        this.expectedMessageEndpoint = expectedMessageEndpoint;
    }

    @Override
    protected void doStart() throws Exception {
        LOG.debug("Consuming expected messages from: {}", expectedMessageEndpoint);

        final List<Object> expectedBodies = new ArrayList<>();
        EndpointHelper.pollEndpoint(expectedMessageEndpoint, new Processor() {
            public void process(Exchange exchange) throws Exception {
                // if file based we need to load the file into memory as the file may be deleted/moved afterwards
                Object body = getInBody(exchange);
                if (body instanceof WrappedFile) {
                    body = exchange.getIn().getBody(String.class);
                }
                if (split) {
                    // use new lines in both styles
                    Iterator<?> it = ObjectHelper.createIterator(body, delimiter, false, true);
                    while (it.hasNext()) {
                        Object line = it.next();
                        LOG.trace("Received message body {}", line);
                        expectedBodies.add(line);
                    }
                } else {
                    expectedBodies.add(body);
                }
            }
        }, timeout);

        LOG.info("Received: {} expected message(s) from: {}", expectedBodies.size(), expectedMessageEndpoint);
        if (anyOrder) {
            expectedBodiesReceivedInAnyOrder(expectedBodies);
        } else {
            expectedBodiesReceived(expectedBodies);
        }
    }

    /**
     * This method allows us to convert or coerce the expected message body into some other type
     */
    protected Object getInBody(Exchange exchange) {
        return exchange.getIn().getBody();
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout to use when polling for message bodies from the URI
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isAnyOrder() {
        return anyOrder;
    }

    /**
     * Whether the expected messages should arrive in the same order or can be in any order.
     */
    public void setAnyOrder(boolean anyOrder) {
        this.anyOrder = anyOrder;
    }

    public boolean isSplit() {
        return split;
    }

    /**
     * If enabled the messages loaded from the test endpoint will be split using new line delimiters so each line is an
     * expected message. <br/>
     * For example to use a file endpoint to load a file where each line is an expected message.
     */
    public void setSplit(boolean split) {
        this.split = split;
    }

    public String getDelimiter() {
        return delimiter;
    }

    /**
     * The split delimiter to use when split is enabled. By default the delimiter is new line based. The delimiter can
     * be a regular expression.
     */
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }
}
