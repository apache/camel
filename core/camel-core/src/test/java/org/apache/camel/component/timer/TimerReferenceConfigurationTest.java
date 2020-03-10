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
package org.apache.camel.component.timer;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class TimerReferenceConfigurationTest extends ContextTestSupport {

    /**
     * reference params
     */
    final String refExpectedTimeString = "1972-12-11 19:55:00";
    final String refExpectedPattern = "yyyy-MM-dd HH:mm:ss";
    final long refExpectedPeriod = 500;
    final long refExpectedDelay = 100;
    final boolean refExpectedFixedRate = true;
    final boolean refExpectedDaemon = false;
    final long refExpectedRepeatCount = 11;

    /**
     * value params
     */
    final String valExpectedTimeString = "1970-04-17T18:07:41";
    final String valExpectedPattern = "yyyy-MM-dd'T'HH:mm:ss";
    final long valExpectedPeriod = 350;
    final long valExpectedDelay = 123;
    final boolean valExpectedFixedRate = false;
    final boolean valExpectedDaemon = true;
    final long valExpectedRepeatCount = 13;

    final String refTimerUri = "timer://passByRefTimer?" + "time=#refExpectedTimeString" + "&pattern=#refExpectedPattern" + "&period=#refExpectedPeriod"
                               + "&delay=#refExpectedDelay" + "&fixedRate=#refExpectedFixedRate" + "&daemon=#refExpectedDaemon" + "&repeatCount=#refExpectedRepeatCount";

    final String valueTimerUri = "timer://passByValueTimer?" + "time=" + valExpectedTimeString + "&pattern=" + valExpectedPattern + "&period=" + valExpectedPeriod + "&delay="
                                 + valExpectedDelay + "&fixedRate=" + valExpectedFixedRate + "&daemon=" + valExpectedDaemon + "&repeatCount=" + valExpectedRepeatCount;

    final String mockEndpointUri = "mock:result";

    @Override
    protected Registry createRegistry() throws Exception {
        Registry reg = super.createRegistry();
        reg.bind("refExpectedTimeString", refExpectedTimeString);
        reg.bind("refExpectedPattern", refExpectedPattern);
        reg.bind("refExpectedPeriod", refExpectedPeriod);
        reg.bind("refExpectedDelay", refExpectedDelay);
        reg.bind("refExpectedFixedRate", refExpectedFixedRate);
        reg.bind("refExpectedDaemon", refExpectedDaemon);
        reg.bind("refExpectedRepeatCount", refExpectedRepeatCount);
        return reg;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(refTimerUri).to(mockEndpointUri);
                from(valueTimerUri).to(mockEndpointUri);
            }
        };
    }

    /**
     * Test that the reference configuration params are correct
     */
    @Test
    public void testReferenceConfiguration() throws Exception {

        Endpoint e = context.getEndpoint(refTimerUri);
        TimerEndpoint timer = (TimerEndpoint)e;
        final Date expectedTimeObject = new SimpleDateFormat(refExpectedPattern).parse(refExpectedTimeString);
        final Date time = timer.getTime();
        final long period = timer.getPeriod();
        final long delay = timer.getDelay();
        final boolean fixedRate = timer.isFixedRate();
        final boolean daemon = timer.isDaemon();
        final long repeatCount = timer.getRepeatCount();

        assertEquals(refExpectedDelay, delay);
        assertEquals(refExpectedPeriod, period);
        assertEquals(expectedTimeObject, time);
        assertEquals(refExpectedFixedRate, fixedRate);
        assertEquals(refExpectedDaemon, daemon);
        assertEquals(refExpectedRepeatCount, repeatCount);
    }

    /**
     * Test that the 'value' configuration params are correct
     */
    @Test
    public void testValueConfiguration() throws Exception {
        Endpoint e = context.getEndpoint(valueTimerUri);
        TimerEndpoint timer = (TimerEndpoint)e;
        final Date expectedTimeObject = new SimpleDateFormat(valExpectedPattern).parse(valExpectedTimeString);
        final Date time = timer.getTime();
        final long period = timer.getPeriod();
        final long delay = timer.getDelay();
        final boolean fixedRate = timer.isFixedRate();
        final boolean daemon = timer.isDaemon();
        final long repeatCount = timer.getRepeatCount();

        assertEquals(valExpectedDelay, delay);
        assertEquals(valExpectedPeriod, period);
        assertEquals(expectedTimeObject, time);
        assertEquals(valExpectedFixedRate, fixedRate);
        assertEquals(valExpectedDaemon, daemon);
        assertEquals(valExpectedRepeatCount, repeatCount);
    }

}
