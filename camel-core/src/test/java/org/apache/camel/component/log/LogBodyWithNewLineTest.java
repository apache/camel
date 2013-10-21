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
package org.apache.camel.component.log;

import java.io.StringWriter;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.log4j.WriterAppender;

public class LogBodyWithNewLineTest extends ContextTestSupport {

    private StringWriter writer;

    public void setUp() throws Exception {
        super.setUp();
        writer = new StringWriter();

        WriterAppender appender = new WriterAppender(new SimpleLayout(), writer);
        appender.setImmediateFlush(true);

        Logger logger = Logger.getRootLogger();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    public void testNoSkip() throws Exception {
        final String ls = System.getProperty("line.separator");
        String body = "1" + ls + "2" + ls + "3";

        template.sendBody("direct:start", body);

        log.info("{}", writer);

        assertTrue(writer.toString().contains(body));
    }

    public void testSkip() throws Exception {
        final String ls = System.getProperty("line.separator");
        String body = "1" + ls + "2" + ls + "3";

        template.sendBody("direct:skip", body);

        log.info("{}", writer);

        assertTrue(writer.toString().contains("123"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("log:logger_name?level=INFO&showAll=true&skipBodyLineSeparator=false");

                from("direct:skip")
                    .to("log:logger_name?level=INFO&showAll=true&skipBodyLineSeparator=true");
            }
        };
    }

}
