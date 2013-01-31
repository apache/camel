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
package org.apache.camel.example.gae;

import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestProcessor.class);
    
    public void process(Exchange exchange) throws Exception {
        UserService userService = UserServiceFactory.getUserService();
        String city = (String)exchange.getIn().removeHeader("city");
        String requestor = userService.getCurrentUser().getEmail();
        String recipient = requestor;
        
        if (exchange.getIn().removeHeader("mailtocurrent") == null) {
            recipient = (String)exchange.getIn().removeHeader("mailto");
        }
        exchange.getIn().setBody(new ReportData(city, recipient, requestor));
        LOGGER.info(requestor + " requested weather data for " + city  + ". Report will be sent to " + recipient);
    }

}
