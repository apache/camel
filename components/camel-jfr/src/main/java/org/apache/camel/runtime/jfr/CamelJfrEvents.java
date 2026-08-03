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
package org.apache.camel.runtime.jfr;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.jfr.Event;
import jdk.jfr.EventType;

/**
 * The runtime events emitted by camel-jfr, and the short names used to address them from the developer console and the
 * CLI.
 *
 * @since 4.22
 */
enum CamelJfrEvents {

    ROUTE("route", CamelRouteEvent.class),
    PROCESSOR("processor", CamelProcessorEvent.class),
    EXCHANGE("exchange", CamelExchangeEvent.class),
    SEND("send", CamelExchangeSendEvent.class),
    FAILED("failed", CamelExchangeFailedEvent.class),
    REDELIVERY("redelivery", CamelRedeliveryEvent.class);

    private final String shortName;
    private final Class<? extends Event> eventClass;

    CamelJfrEvents(String shortName, Class<? extends Event> eventClass) {
        this.shortName = shortName;
        this.eventClass = eventClass;
    }

    String getShortName() {
        return shortName;
    }

    Class<? extends Event> getEventClass() {
        return eventClass;
    }

    /**
     * The JFR event name, as declared by {@code @Name} on the event class. This is what a {@code .jfc} settings file
     * and {@code jcmd} refer to, and it is not derived from the Java class name.
     */
    String getEventName() {
        return EventType.getEventType(eventClass).getName();
    }

    boolean isEnabled() {
        return EventType.getEventType(eventClass).isEnabled();
    }

    /**
     * @return the event for the given short name, or {@code null} if unknown
     */
    static CamelJfrEvents byShortName(String shortName) {
        for (CamelJfrEvents e : values()) {
            if (e.shortName.equals(shortName)) {
                return e;
            }
        }
        return null;
    }

    /**
     * @return all short names, comma separated, for use in error messages
     */
    static String shortNames() {
        return Stream.of(values()).map(CamelJfrEvents::getShortName).collect(Collectors.joining(", "));
    }
}
