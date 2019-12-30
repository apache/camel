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
package org.apache.camel.component.jmx;

import javax.management.AttributeChangeNotification;
import javax.management.AttributeChangeNotificationFilter;
import javax.management.Notification;

/**
 * {@link javax.management.NotificationFilter} that observes an attribute and optionally
 * matches when the new value matches a string.
 */
public class JMXConsumerNotificationFilter extends AttributeChangeNotificationFilter {

    private final String stringToCompare;
    private final boolean notifyMatch;

    public JMXConsumerNotificationFilter(String observedAttribute, String stringToCompare, boolean notifyMatch) {
        enableAttribute(observedAttribute);
        this.stringToCompare = stringToCompare;
        this.notifyMatch = notifyMatch;
    }

    @Override
    public synchronized boolean isNotificationEnabled(Notification notification) {
        boolean enabled = super.isNotificationEnabled(notification);
        if (!enabled) {
            return false;
        }

        boolean match = false;
        if (stringToCompare != null) {
            AttributeChangeNotification acn = (AttributeChangeNotification) notification;
            Object newValue = acn.getNewValue();
            // special for null
            if ("null".equals(stringToCompare) && newValue == null) {
                match = true;
            } else if (newValue != null) {
                match = stringToCompare.equals(newValue.toString());
            }
            return notifyMatch == match;
        } else {
            return true;
        }
    }

}
