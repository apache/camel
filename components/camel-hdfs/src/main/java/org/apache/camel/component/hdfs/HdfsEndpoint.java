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
package org.apache.camel.component.hdfs;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Read and write from/to an HDFS filesystem using Hadoop 2.x.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "hdfs", title = "HDFS", syntax = "hdfs:hostName:port/path",
             category = { Category.BIGDATA, Category.FILE }, headersClass = HdfsConstants.class)
public class HdfsEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private final HdfsConfiguration config;

    public HdfsEndpoint(String endpointUri, HdfsComponent component) throws URISyntaxException {
        super(endpointUri, component);
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

    public HdfsConfiguration getConfig() {
        return config;
    }

}
