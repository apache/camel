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

import java.io.File;
import java.io.InputStream;
import java.net.URI;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Small unit test which verifies ical data format.
 */
public class ICalDataFormatTest extends CamelTestSupport {

    private java.util.TimeZone defaultTimeZone;

    @Override
    public void doPostSetup() {
        defaultTimeZone = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));
    }

    @Override
    public void doPostTearDown() {
        java.util.TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    public void testUnmarshal() throws Exception {
        InputStream stream = IOConverter.toInputStream(new File("src/test/resources/data.ics"));
        MockEndpoint endpoint = getMockEndpoint("mock:result");

        endpoint.expectedBodiesReceived(createTestCalendar());

        template.sendBody("direct:unmarshal", stream);

        endpoint.assertIsSatisfied();
    }

    @Test
    public void testMarshal() throws Exception {
        Calendar testCalendar = createTestCalendar();
        MockEndpoint endpoint = getMockEndpoint("mock:result");

        endpoint.expectedBodiesReceived(testCalendar.toString());

        template.sendBody("direct:marshal", testCalendar);

        endpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal")
                        .unmarshal().ical(true)
                        .to("mock:result");
                from("direct:marshal")
                        .marshal("ical")
                        .to("mock:result");
            }
        };
    }

    /**
     * Creates test calendar instance.
     *
     * @return ICal calendar object.
     */
    protected Calendar createTestCalendar() {
        // Create a TimeZone
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        TimeZone timezone = registry.getTimeZone("America/New_York");
        VTimeZone tz = timezone.getVTimeZone();

        // Create the event
        VEvent meeting = new VEvent();
        meeting.replace(new DtStamp("20130324T180000Z"));
        meeting.add(new DtStart("20130401T170000"));
        meeting.add(new DtEnd("20130401T210000"));
        meeting.add(new Summary("Progress Meeting"));

        // add timezone info..
        meeting.add(tz.getTimeZoneId());

        // generate unique identifier..
        meeting.add(new Uid("00000000"));

        // add attendees..
        Attendee dev1 = new Attendee(URI.create("mailto:dev1@mycompany.com"));
        dev1.add(Role.REQ_PARTICIPANT);
        dev1.add(new Cn("Developer 1"));
        meeting.add(dev1);

        Attendee dev2 = new Attendee(URI.create("mailto:dev2@mycompany.com"));
        dev2.add(Role.OPT_PARTICIPANT);
        dev2.add(new Cn("Developer 2"));
        meeting.add(dev2);

        // Create a calendar
        net.fortuna.ical4j.model.Calendar icsCalendar = new net.fortuna.ical4j.model.Calendar();
        icsCalendar.add(ImmutableVersion.VERSION_2_0);
        icsCalendar.add(new ProdId("-//Events Calendar//iCal4j 1.0//EN"));
        icsCalendar.add(ImmutableCalScale.GREGORIAN);

        // Add the event and print
        icsCalendar.add(meeting);
        return icsCalendar;
    }

}
