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
package org.apache.camel.commands;

import java.io.PrintStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.util.RouteStatDump;

import static org.apache.camel.util.ObjectHelper.isEmpty;

/**
 * Command to display detailed information about a Camel route.
 */
public class RouteInfoCommand extends AbstractRouteCommand {

    public static final String XML_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    public static final String OUTPUT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private StringEscape stringEscape;

    public RouteInfoCommand(String route, String context) {
        super(route, context);
    }

    /**
     * Sets the {@link org.apache.camel.commands.StringEscape} to use.
     */
    public void setStringEscape(StringEscape stringEscape) {
        this.stringEscape = stringEscape;
    }

    @Override
    public void executeOnRoute(CamelController camelController, String contextName, String routeId, PrintStream out, PrintStream err) throws Exception {
        out.println(stringEscape.unescapeJava("\u001B[1mCamel Route " + routeId + "\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tCamel Context: " + contextName));

        String xml = camelController.getRouteStatsAsXml(routeId, contextName, true, false);
        if (xml != null) {
            JAXBContext context = JAXBContext.newInstance(RouteStatDump.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            RouteStatDump route = (RouteStatDump) unmarshaller.unmarshal(new StringReader(xml));
            out.println(stringEscape.unescapeJava("\tState: " + route.getState()));
            out.println(stringEscape.unescapeJava("\tState: " + route.getState()));

            out.println("");
            out.println("");
            out.println(stringEscape.unescapeJava("\u001B[1mStatistics\u001B[0m"));
            long total = route.getExchangesCompleted() + route.getExchangesFailed();
            out.println(stringEscape.unescapeJava("\tExchanges Total: " + total));
            out.println(stringEscape.unescapeJava("\tExchanges Completed: " + route.getExchangesCompleted()));
            out.println(stringEscape.unescapeJava("\tExchanges Failed: " + route.getExchangesFailed()));
            out.println(stringEscape.unescapeJava("\tExchanges Inflight: " + route.getExchangesInflight()));
            out.println(stringEscape.unescapeJava("\tMin Processing Time: " + route.getMinProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tMax Processing Time: " + route.getMaxProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tMean Processing Time: " + route.getMeanProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tTotal Processing Time: " + route.getTotalProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tLast Processing Time: " + route.getLastProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tDelta Processing Time: " + route.getDeltaProcessingTime() + " ms"));

            if (isEmpty(route.getStartTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tStart Statistics Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(route.getStartTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tStart Statistics Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (isEmpty(route.getResetTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tReset Statistics Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(route.getResetTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tReset Statistics Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (isEmpty(route.getFirstExchangeCompletedTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tFirst Exchange Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(route.getFirstExchangeCompletedTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tFirst Exchange Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (isEmpty(route.getLastExchangeCompletedTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tLast Exchange Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(route.getLastExchangeCompletedTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tLast Exchange Date: " + text));
            }
        }
    }
}
