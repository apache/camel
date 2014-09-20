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
package org.apache.camel.example.pojo_messaging;

import org.apache.camel.Consume;
import org.apache.camel.Header;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//START SNIPPET: ex
public class SendFileRecordsToQueueBean {

    private static final Logger LOG = LoggerFactory.getLogger(SendFileRecordsToQueueBean.class);

    @Produce(uri = "activemq:personnel.records")
    ProducerTemplate producer;

    @Consume(uri = "file:src/data?noop=true")
    public void onFileSendToQueue(String body, @Header("CamelFileName") String name) {
        LOG.info("Incoming file: {}", name);
        producer.sendBody(body);
    }
}
//END SNIPPET: ex

