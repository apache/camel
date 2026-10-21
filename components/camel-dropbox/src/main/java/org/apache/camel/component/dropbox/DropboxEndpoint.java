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
package org.apache.camel.component.dropbox;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.dropbox.integration.consumer.DropboxScheduledPollConsumer;
import org.apache.camel.component.dropbox.integration.consumer.DropboxScheduledPollGetConsumer;
import org.apache.camel.component.dropbox.integration.consumer.DropboxScheduledPollSearchConsumer;
import org.apache.camel.component.dropbox.integration.producer.DropboxDelProducer;
import org.apache.camel.component.dropbox.integration.producer.DropboxGetProducer;
import org.apache.camel.component.dropbox.integration.producer.DropboxMoveProducer;
import org.apache.camel.component.dropbox.integration.producer.DropboxPutProducer;
import org.apache.camel.component.dropbox.integration.producer.DropboxSearchProducer;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxException;
import org.apache.camel.component.dropbox.util.DropboxOperation;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upload, download and manage files, folders, groups, collaborations, etc on Dropbox.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "dropbox", title = "Dropbox", syntax = "dropbox:operation",
             category = { Category.CLOUD, Category.FILE, Category.API }, headersClass = DropboxConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "get",
                                                          carriers = { Carrier.STREAM, Carrier.BYTES, Carrier.MAP }),
                                           @BodyType.When(option = "operation", value = "search", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.dropbox.core.v2.files.SearchMatchV2>") },
                                   description = "One message per poll. get: the content of the file in remotePath as a stream"
                                                 + " cache (a byte array when stream caching is off), or for a folder a Map of"
                                                 + " the path to the content of each file. search: a List of SearchMatchV2"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Only put reads the body, unless localPath or the CamelDropboxLocalPath header"
                                                + " names a local file or folder to upload: the content to upload, as text,"
                                                + " bytes, a stream or a file. A Map or a List is not uploaded, and the result"
                                                + " is KO without an error: marshal it first"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "put",
                                                          carriers = { Carrier.VALUE, Carrier.MAP }),
                                           @BodyType.When(option = "operation", value = "get",
                                                          carriers = { Carrier.STREAM, Carrier.BYTES, Carrier.MAP }),
                                           @BodyType.When(option = "operation", value = "del", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "move", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "search", carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.dropbox.core.v2.files.SearchMatchV2>") },
                                   description = "put: OK or KO, a DropboxResultCode, or for a local folder of several files a"
                                                 + " Map of each remote path to its result. get: the content of the file as a"
                                                 + " stream cache (a byte array when stream caching is off), or a Map of the path"
                                                 + " to the content of each file of a folder. del: the path deleted. move: the new"
                                                 + " path. search: a List of SearchMatchV2"))
public class DropboxEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(DropboxEndpoint.class);

    @UriParam
    private DropboxConfiguration configuration;

    public DropboxEndpoint() {
    }

    public DropboxEndpoint(String uri, DropboxComponent component, DropboxConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return "api.dropboxapi.com";
    }

    @Override
    public String getServiceProtocol() {
        return "rest";
    }

    public DropboxConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Create one of the camel producer available based on the configuration
     *
     * @return           the camel producer
     * @throws Exception
     */
    @Override
    public Producer createProducer() throws Exception {
        LOG.trace("Resolve producer dropbox endpoint {{}}", configuration.getOperation());
        LOG.trace("Resolve producer dropbox attached client: {}", configuration.getClient());
        if (configuration.getOperation() == DropboxOperation.put) {
            return new DropboxPutProducer(this, configuration);
        } else if (this.configuration.getOperation() == DropboxOperation.search) {
            return new DropboxSearchProducer(this, configuration);
        } else if (this.configuration.getOperation() == DropboxOperation.del) {
            return new DropboxDelProducer(this, configuration);
        } else if (this.configuration.getOperation() == DropboxOperation.get) {
            return new DropboxGetProducer(this, configuration);
        } else if (this.configuration.getOperation() == DropboxOperation.move) {
            return new DropboxMoveProducer(this, configuration);
        } else {
            throw new DropboxException("Operation specified is not valid for producer!");
        }
    }

    /**
     * Create one of the camel consumer available based on the configuration
     *
     * @param  processor the given processor
     * @return           the camel consumer
     * @throws Exception
     */
    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace("Resolve consumer dropbox endpoint {{}}", configuration.getOperation());
        LOG.trace("Resolve consumer dropbox attached client: {}", configuration.getClient());
        DropboxScheduledPollConsumer consumer;
        if (this.configuration.getOperation() == DropboxOperation.search) {
            consumer = new DropboxScheduledPollSearchConsumer(this, processor, configuration);
            consumer.setDelay(DropboxConstants.POLL_CONSUMER_DELAY);
            configureConsumer(consumer);
            return consumer;
        } else if (this.configuration.getOperation() == DropboxOperation.get) {
            consumer = new DropboxScheduledPollGetConsumer(this, processor, configuration);
            consumer.setDelay(DropboxConstants.POLL_CONSUMER_DELAY);
            configureConsumer(consumer);
            return consumer;
        } else {
            throw new DropboxException("Operation specified is not valid for consumer!");
        }
    }

}
