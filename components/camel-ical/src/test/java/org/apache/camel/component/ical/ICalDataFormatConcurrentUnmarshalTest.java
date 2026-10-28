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
package org.apache.camel.component.ical;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two exchanges are unmarshalled by the same data format at the same time. The stream of the first one stops between
 * its two events until the second exchange has been unmarshalled, so the two calls always overlap.
 */
class ICalDataFormatConcurrentUnmarshalTest extends CamelTestSupport {

    private static final String HEAD = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Events Calendar//iCal4j 1.0//EN\r\n";

    @Test
    void overlappingUnmarshalsGetTheirOwnCalendar() throws Exception {
        CountDownLatch firstIsBetweenItsEvents = new CountDownLatch(1);
        CountDownLatch secondIsDone = new CountDownLatch(1);

        // the stream ends in the middle of a line, so the parser has handled the first event completely and has not
        // started the second one when it asks for more
        byte[] firstPart = (HEAD + event("a1") + "BEGIN:VEV").getBytes(StandardCharsets.UTF_8);
        byte[] secondPart = ("ENT\r\nUID:a2\r\nSUMMARY:a2\r\nEND:VEVENT\r\n" + "END:VCALENDAR\r\n")
                .getBytes(StandardCharsets.UTF_8);
        InputStream first = new TwoPartStream(firstPart, secondPart, firstIsBetweenItsEvents, secondIsDone);
        byte[] second = (HEAD + event("b1") + event("b2") + "END:VCALENDAR\r\n").getBytes(StandardCharsets.UTF_8);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Calendar> firstResult
                    = executor.submit(() -> template.requestBody("direct:unmarshal", first, Calendar.class));

            assertTrue(firstIsBetweenItsEvents.await(20, TimeUnit.SECONDS), "the first unmarshal did not read its stream");
            Calendar secondCalendar;
            try {
                secondCalendar = template.requestBody("direct:unmarshal", second, Calendar.class);
            } finally {
                secondIsDone.countDown();
            }
            Calendar firstCalendar = firstResult.get(20, TimeUnit.SECONDS);

            assertEquals(List.of("a1", "a2"), summaries(firstCalendar), "events of the first calendar");
            assertEquals(List.of("b1", "b2"), summaries(secondCalendar), "events of the second calendar");
        } finally {
            secondIsDone.countDown();
            executor.shutdownNow();
        }
    }

    private static String event(String name) {
        return "BEGIN:VEVENT\r\nUID:" + name + "\r\nSUMMARY:" + name + "\r\nEND:VEVENT\r\n";
    }

    private static List<String> summaries(Calendar calendar) {
        return calendar.<CalendarComponent> getComponents(Component.VEVENT).stream()
                .map(event -> event.getRequiredProperty(Property.SUMMARY).getValue())
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // without stream caching the data format reads the stream of the test itself, so the test can hold
                // the first call inside unmarshal; the two calls would overlap with stream caching too, but not at
                // a point the test controls
                from("direct:unmarshal").noStreamCaching().unmarshal().ical(false);
            }
        };
    }

    /**
     * Returns the first part, then reports that it was read and waits (bounded) for the gate before it returns the
     * second part.
     */
    private static final class TwoPartStream extends InputStream {
        private final byte[] firstPart;
        private final byte[] secondPart;
        private final CountDownLatch firstPartRead;
        private final CountDownLatch gate;
        private int position;
        private boolean gatePassed;

        private TwoPartStream(byte[] firstPart, byte[] secondPart, CountDownLatch firstPartRead, CountDownLatch gate) {
            this.firstPart = firstPart;
            this.secondPart = secondPart;
            this.firstPartRead = firstPartRead;
            this.gate = gate;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (position < firstPart.length) {
                int count = Math.min(length, firstPart.length - position);
                System.arraycopy(firstPart, position, target, offset, count);
                position += count;
                return count;
            }
            if (!gatePassed) {
                firstPartRead.countDown();
                try {
                    if (!gate.await(20, TimeUnit.SECONDS)) {
                        throw new IOException("the second unmarshal did not finish in time");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
                gatePassed = true;
            }
            int done = position - firstPart.length;
            if (done >= secondPart.length) {
                return -1;
            }
            int count = Math.min(length, secondPart.length - done);
            System.arraycopy(secondPart, done, target, offset, count);
            position += count;
            return count;
        }
    }
}
