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
package org.apache.camel.example.mention;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.User;

@Component
public class TwitterSalesforceRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("twitter-timeline:mentions")
            .log("Tweet id ${body.id} mention: ${body}")
            .process(exchange -> {
                Status status = exchange.getIn().getBody(Status.class);
                User user = status.getUser();
                String name = user.getName();
                String screenName = user.getScreenName();
                Contact contact = new Contact(name, screenName);
                exchange.getIn().setBody(contact);
            })
            .to("salesforce:upsertSObject?sObjectIdName=TwitterScreenName__c")
            .log("SObject ID: ${body?.id}");
    }

}
