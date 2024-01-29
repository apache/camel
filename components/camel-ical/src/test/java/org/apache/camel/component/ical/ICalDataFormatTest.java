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
import java.text.ParseException;
import java.util.GregorianCalendar;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Small unit test which verifies ical data format.
 */
public class ICalDataFormatTest extends CamelTestSupport {

    private java.util.TimeZone defaultTimeZone;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        defaultTimeZone = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"));

        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        java.util.TimeZone.setDefault(defaultTimeZone);

        super.tearDown();
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
    protected Calendar createTestCalendar() throws ParseException {
        // Create a TimeZone
        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
        TimeZone timezone = registry.getTimeZone("America/New_York");
        VTimeZone tz = timezone.getVTimeZone();

        // Start Date is on: April 1, 2013, 9:00 am
        java.util.Calendar startDate = new GregorianCalendar();
        startDate.setTimeZone(timezone);
        startDate.set(java.util.Calendar.MONTH, java.util.Calendar.APRIL);
        startDate.set(java.util.Calendar.DAY_OF_MONTH, 1);
        startDate.set(java.util.Calendar.YEAR, 2013);
        startDate.set(java.util.Calendar.HOUR_OF_DAY, 17);
        startDate.set(java.util.Calendar.MINUTE, 0);
        startDate.set(java.util.Calendar.SECOND, 0);

        // End Date is on: April 1, 2013, 13:00
        java.util.Calendar endDate = new GregorianCalendar();
        endDate.setTimeZone(timezone);
        endDate.set(java.util.Calendar.MONTH, java.util.Calendar.APRIL);
        endDate.set(java.util.Calendar.DAY_OF_MONTH, 1);
        endDate.set(java.util.Calendar.YEAR, 2013);
        endDate.set(java.util.Calendar.HOUR_OF_DAY, 21);
        endDate.set(java.util.Calendar.MINUTE, 0);
        endDate.set(java.util.Calendar.SECOND, 0);

        // Create the event
        PropertyList propertyList = new PropertyList();
        propertyList.add(new DtStamp("20130324T180000Z"));
        propertyList.add(new DtStart(new DateTime(startDate.getTime())));
        propertyList.add(new DtEnd(new DateTime(endDate.getTime())));
        propertyList.add(new Summary("Progress Meeting"));
        VEvent meeting = new VEvent(propertyList);

        // add timezone info..
        meeting.getProperties().add(tz.getTimeZoneId());

        // generate unique identifier..
        meeting.getProperties().add(new Uid("00000000"));

        // add attendees..
        Attendee dev1 = new Attendee(URI.create("mailto:dev1@mycompany.com"));
        dev1.getParameters().add(Role.REQ_PARTICIPANT);
        dev1.getParameters().add(new Cn("Developer 1"));
        meeting.getProperties().add(dev1);

        Attendee dev2 = new Attendee(URI.create("mailto:dev2@mycompany.com"));
        dev2.getParameters().add(Role.OPT_PARTICIPANT);
        dev2.getParameters().add(new Cn("Developer 2"));
        meeting.getProperties().add(dev2);

        // Create a calendar
        net.fortuna.ical4j.model.Calendar icsCalendar = new net.fortuna.ical4j.model.Calendar();
        icsCalendar.getProperties().add(Version.VERSION_2_0);
        icsCalendar.getProperties().add(new ProdId("-//Events Calendar//iCal4j 1.0//EN"));
        icsCalendar.getProperties().add(CalScale.GREGORIAN);

        // Add the event and print
        icsCalendar.getComponents().add(meeting);
        return icsCalendar;
    }

}
