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

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.util.RouteStatDump;

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
    public void executeOnRoute(CamelController camelController, CamelContext camelContext, Route camelRoute, PrintStream out, PrintStream err) throws Exception {
        out.println(stringEscape.unescapeJava("\u001B[1m\u001B[33mCamel Route " + camelRoute.getId() + "\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tCamel Context: " + camelRoute.getRouteContext().getCamelContext().getName()));
        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mStatistics\u001B[0m"));

        String xml = camelController.getRouteStatsAsXml(camelRoute.getId(), camelContext.getName(), true, false);
        if (xml != null) {
            JAXBContext context = JAXBContext.newInstance(RouteStatDump.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            RouteStatDump route = (RouteStatDump) unmarshaller.unmarshal(new StringReader(xml));

            out.println(stringEscape.unescapeJava("\tExchanges Total: " + route.getExchangesCompleted() + route.getExchangesFailed()));
            out.println(stringEscape.unescapeJava("\tExchanges Completed: " + route.getExchangesCompleted()));
            out.println(stringEscape.unescapeJava("\tExchanges Failed: " + route.getExchangesFailed()));
            out.println(stringEscape.unescapeJava("\tMin Processing Time: " + route.getMinProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tMax Processing Time: " + route.getMaxProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tMean Processing Time: " + route.getMeanProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tTotal Processing Time: " + route.getTotalProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tLast Processing Time: " + route.getLastProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tDelta Processing Time: " + route.getDeltaProcessingTime() + " ms"));

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (route.getResetTimestamp() == null) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tReset Statistics Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(route.getResetTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tReset Statistics Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (route.getFirstExchangeCompletedTimestamp() == null) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tFirst Exchange Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(route.getFirstExchangeCompletedTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tFirst Exchange Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (route.getLastExchangeCompletedTimestamp() == null) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tLast Exchange Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(route.getLastExchangeCompletedTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tLast Exchange Date: " + text));
            }

            out.println("");
            xml = camelController.getRouteModelAsXml(camelRoute.getId(), camelRoute.getRouteContext().getCamelContext().getName());
            if (xml != null) {
                out.println(stringEscape.unescapeJava("\u001B[1mDefinition\u001B[0m"));
                out.println(stringEscape.unescapeJava(xml));
            }
        }
    }
}
