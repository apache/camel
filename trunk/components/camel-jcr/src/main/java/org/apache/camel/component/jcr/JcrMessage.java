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
package org.apache.camel.component.jcr;

import java.util.LinkedList;
import java.util.List;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a {@link org.apache.camel.Message} for working with JCR
 *
 * @version $Id$
 */
public class JcrMessage extends DefaultMessage {

    private EventIterator eventIterator;
    private List<Event> eventList;

    public JcrMessage(EventIterator eventIterator) {
        this.eventIterator = eventIterator;
    }

    @Override
    public String toString() {
        if (eventIterator != null) {
            return "JcrMessage[eventIterator: " + eventIterator + ", eventList: " + eventList + "]";
        }

        return "JcrMessage@" + ObjectHelper.getIdentityHashCode(this);
    }

    @Override
    public void copyFrom(org.apache.camel.Message that) {
        if (that == this) {
            // the same instance so do not need to copy
            return;
        }

        // must initialize headers before we set the JmsMessage to avoid Camel
        // populating it before we do the copy
        getHeaders().clear();

        if (that instanceof JcrMessage) {
            JcrMessage thatMessage = (JcrMessage) that;
            this.eventIterator = thatMessage.eventIterator;
            this.eventList = thatMessage.eventList;
        }

        // copy body and fault flag
        setBody(that.getBody());
        setFault(that.isFault());

        // we have already cleared the headers
        if (that.hasHeaders()) {
            getHeaders().putAll(that.getHeaders());
        }

        getAttachments().clear();

        if (that.hasAttachments()) {
            getAttachments().putAll(that.getAttachments());
        }
    }

    public EventIterator getEventIterator() {
        return eventIterator;
    }

    @Override
    protected Object createBody() {
        if (eventList == null) {
            eventList = new LinkedList<Event>();

            if (eventIterator != null) {
                while (eventIterator.hasNext()) {
                    eventList.add(eventIterator.nextEvent());
                }
            }
        }

        return eventList;
    }
}
