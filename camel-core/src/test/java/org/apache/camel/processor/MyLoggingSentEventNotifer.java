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
package org.apache.camel.processor;

import java.util.EventObject;

import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * @version 
 */
// START SNIPPET: e1
public class MyLoggingSentEventNotifer extends EventNotifierSupport {

    public void notify(EventObject event) throws Exception {

        if (event instanceof ExchangeSentEvent) {
            ExchangeSentEvent sent = (ExchangeSentEvent) event;
            log.info("Took {} millis to send to: {}", sent.getTimeTaken(), sent.getEndpoint());
        }

    }

    public boolean isEnabled(EventObject event) {
        // we only want the sent events
        return event instanceof ExchangeSentEvent;
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        // noop
    }

}
// END SNIPPET: e1
