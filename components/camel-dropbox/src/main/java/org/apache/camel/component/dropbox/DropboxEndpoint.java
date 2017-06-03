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
package org.apache.camel.component.dropbox;

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
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For uploading, downloading and managing files, folders, groups, collaborations, etc on dropbox DOT com.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "dropbox", title = "Dropbox", syntax = "dropbox:operation", consumerClass = DropboxScheduledPollConsumer.class, label = "api,file")
public class DropboxEndpoint extends DefaultEndpoint {

    private static final transient Logger LOG = LoggerFactory.getLogger(DropboxEndpoint.class);

    @UriParam
    private DropboxConfiguration configuration;

    public DropboxEndpoint() {
    }

    public DropboxEndpoint(String uri, DropboxComponent component, DropboxConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public DropboxEndpoint(String endpointUri) {
        super(endpointUri);
    }

    /**
     * Create one of the camel producer available based on the configuration
     * @return the camel producer
     * @throws Exception
     */
    public Producer createProducer() throws Exception {
        LOG.trace("Resolve producer dropbox endpoint {" + configuration.getOperation().toString() + "}");
        LOG.trace("Resolve producer dropbox attached client: " + configuration.getClient());
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
     * @param processor  the given processor
     * @return the camel consumer
     * @throws Exception
     */
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.trace("Resolve consumer dropbox endpoint {" + configuration.getOperation().toString() + "}");
        LOG.trace("Resolve consumer dropbox attached client:" + configuration.getClient());
        DropboxScheduledPollConsumer consumer;
        if (this.configuration.getOperation() == DropboxOperation.search) {
            consumer = new DropboxScheduledPollSearchConsumer(this, processor, configuration);
            consumer.setDelay(DropboxConstants.POLL_CONSUMER_DELAY);
            return consumer;
        } else if (this.configuration.getOperation() == DropboxOperation.get) {
            consumer = new DropboxScheduledPollGetConsumer(this, processor, configuration);
            consumer.setDelay(DropboxConstants.POLL_CONSUMER_DELAY);
            return consumer;
        } else {
            throw new DropboxException("Operation specified is not valid for consumer!");
        }
    }

    public boolean isSingleton() {
        return true;
    }
}
