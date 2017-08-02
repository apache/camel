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
package org.apache.camel.component.hdfs2;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * For reading/writing from/to an HDFS filesystem using Hadoop 2.x.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "hdfs2", title = "HDFS2", syntax = "hdfs2:hostName:port/path", consumerClass = HdfsConsumer.class, label = "hadoop,file")
public class HdfsEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private final HdfsConfiguration config;

    @SuppressWarnings("deprecation")
    public HdfsEndpoint(String endpointUri, CamelContext context) throws URISyntaxException {
        super(endpointUri, context);
        this.config = new HdfsConfiguration();
        this.config.parseURI(new URI(endpointUri));
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        config.checkConsumerOptions();
        HdfsConsumer answer = new HdfsConsumer(this, processor, config);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() {
        config.checkProducerOptions();
        return new HdfsProducer(this, config);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public HdfsConfiguration getConfig() {
        return config;
    }

}
