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
package org.apache.camel.jaxb;

import org.apache.camel.Exchange;

/**
 * NotificationType does not have any JAXB ObjectFactory so we should test you can still route using that
 */
public class MyNotificationService {

    public void createNotification(Exchange exchange) {
        NotificationType notification = new NotificationType();
        notification.setEvent("Hello");

        exchange.getOut().setBody(notification);
    }

    public void sendNotification(Exchange exchange) {
        NotificationType notification = (NotificationType) exchange.getIn().getBody();

        exchange.getContext().createProducerTemplate().sendBody("mock:notify", notification);
    }

}
