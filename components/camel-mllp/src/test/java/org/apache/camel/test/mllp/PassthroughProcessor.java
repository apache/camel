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
package org.apache.camel.test.mllp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Passthrough test processor
 *
 * Used to set breakpoints and diagnose what is going on in test routes
 */
public class PassthroughProcessor implements Processor {
    String id;
    Logger log = LoggerFactory.getLogger(this.getClass());

    public PassthroughProcessor(String id) {
        this.id = id;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String message = exchange.getIn().getBody(String.class);
        if (null != message) {
            String msh = message.substring(0, message.indexOf('\r'));
            log.debug("Processing MSH {}: \n{}\n", id, msh);
        }

        log.debug("Null inbound message body");
    }
}
