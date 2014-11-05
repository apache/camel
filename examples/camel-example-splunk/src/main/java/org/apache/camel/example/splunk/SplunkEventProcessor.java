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
package org.apache.camel.example.splunk;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.splunk.event.SplunkEvent;

public class SplunkEventProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        SplunkEvent splunkEvent = new SplunkEvent();

        splunkEvent.addPair("ERRORKEY", "AVUA123");
        splunkEvent.addPair("ERRORMSG", "Service ABC Failed");
        splunkEvent.addPair("ERRORDESC", "BusinessException: Username and password don't match");
        splunkEvent.addPair(SplunkEvent.COMMON_START_TIME, "Thu Aug 15 2014 00:15:06");

        exchange.getIn().setBody(splunkEvent, SplunkEvent.class);
    }
}
