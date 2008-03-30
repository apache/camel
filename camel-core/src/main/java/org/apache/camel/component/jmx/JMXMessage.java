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
package org.apache.camel.component.jmx;

import javax.management.Notification;
import org.apache.camel.impl.DefaultMessage;

/**
 * A {@link org.apache.camel.Message Message} for a JMX Notification
 * 
 * @version $Revision$
 */
public class JMXMessage extends DefaultMessage {

    private Notification notification;

    public JMXMessage() {
        this(null);
    }

    public JMXMessage(Notification notification) {
        this.notification = notification;
    }

    @Override
    public String toString() {
        return "JMXMessage: " + notification;
    }

    @Override
    public JMXExchange getExchange() {
        return (JMXExchange)super.getExchange();
    }

    @Override
    public JMXMessage newInstance() {
        return new JMXMessage();
    }

    public Notification getNotification() {
        return notification;
    }
}
