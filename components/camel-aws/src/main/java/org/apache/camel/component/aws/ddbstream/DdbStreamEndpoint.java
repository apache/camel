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
package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.services.dynamodbv2.model.Record;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The aws-ddbstream component is used for working with Amazon DynamoDB Streams.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "aws-ddbstream", title = "AWS DynamoDB Streams",
        consumerOnly = true, syntax = "aws-ddbstream:tableName",
        consumerClass = DdbStreamConsumer.class, label = "cloud,messaging,streams")
public class DdbStreamEndpoint extends ScheduledPollEndpoint {

    @UriParam
    DdbStreamConfiguration configuration;

    public DdbStreamEndpoint(String uri, DdbStreamConfiguration configuration, DdbStreamComponent component) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DdbStreamConsumer consumer = new DdbStreamConsumer(this, processor);
        consumer.setSchedulerProperties(consumer.getEndpoint().getSchedulerProperties());
        configureConsumer(consumer);
        return consumer;
    }

    Exchange createExchange(Record record) {
        Exchange ex = super.createExchange();
        ex.getIn().setBody(record, Record.class);

        return ex;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public DdbStreamConfiguration getConfiguration() {
        return configuration;
    }

    public String getSequenceNumber() {
        switch (configuration.getIteratorType()) {
        case AFTER_SEQUENCE_NUMBER:
        case AT_SEQUENCE_NUMBER:
            if (null == configuration.getSequenceNumberProvider()) {
                throw new IllegalStateException("sequenceNumberProvider must be"
                        + " provided, either as an implementation of"
                        + " SequenceNumberProvider or a literal String.");
            } else {
                return configuration.getSequenceNumberProvider().getSequenceNumber();
            }
        default:
            return "";
        }
    }

    @Override
    public String toString() {
        return "DdbStreamEndpoint{"
                + "tableName=" + configuration.getTableName()
                + ", amazonDynamoDbStreamsClient=[redacted], maxResultsPerRequest=" + configuration.getMaxResultsPerRequest()
                + ", iteratorType=" + configuration.getIteratorType()
                + ", sequenceNumberProvider=" + configuration.getSequenceNumberProvider()
                + ", uri=" + getEndpointUri()
                + '}';
    }
}
