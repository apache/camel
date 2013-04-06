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
package org.apache.camel.component.guava.eventbus;

import com.google.common.eventbus.Subscribe;
import org.apache.camel.Processor;

/**
 * Subtype of CamelEventHandler with public method marked with Guava @Subscribe annotation. Supports
 * filtering the messages by event type on the Camel level.
 */
public class FilteringCamelEventHandler extends CamelEventHandler {

    private final Class<?> eventClass;

    public FilteringCamelEventHandler(GuavaEventBusEndpoint eventBusEndpoint, Processor processor, Class<?> eventClass) {
        super(eventBusEndpoint, processor);
        this.eventClass = eventClass;
    }

    /**
     * Guava callback executed when an event was received.
     *
     * @param event the event
     */
    @Subscribe
    public void eventReceived(Object event) {
        if (eventClass == null || eventClass.isAssignableFrom(event.getClass())) {
            doEventReceived(event);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Cannot process event: {} as its class type: {} is not assignable with: {}",
                        new Object[]{event, event.getClass().getName(), eventClass.getName()});
            }
        }
    }

}
