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
package org.apache.camel.spring;

import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.Type;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event notifier which is executed just after Camel has been started, and before Camel is being stopped.
 */
public class StartAndStopEventNotifier extends EventNotifierSupport implements CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(StartAndStopEventNotifier.class);

    private CamelContext camelContext;
    private ProducerTemplate template;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void notify(CamelEvent event) throws Exception {
        // Note: there is also a CamelContextStartingEvent which is send first
        // and then Camel is starting. And when all that is done this event
        // (CamelContextStartedEvent) is send
        Path directory = SendMessageOnRouteStartAndStopTest.getTestDirectory();
        //        Path directory = SpringTestSupport.testDirectory(SendMessageOnRouteStartAndStopTest.class, false);
        if (event.getType() == Type.CamelContextStarted) {
            LOG.info("Sending a message on startup...");
            template.sendBody("file:" + directory + "?fileName=start.txt", "Starting");
        } else if (event.getType() == Type.CamelContextStopping) {
            // Note: there is also a CamelContextStoppedEvent which is send
            // afterwards, when Camel has been fully stopped.
            LOG.info("Sending a message on stopping...");
            template.sendBody("file:" + directory + "?fileName=stop.txt", "Stopping");
        }
    }

    @Override
    public boolean isEnabled(CamelEvent event) {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        template = camelContext.createProducerTemplate();
        template.start();
    }

    @Override
    protected void doStop() throws Exception {
        template.stop();
    }

}
