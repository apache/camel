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
package org.apache.camel.commands;

import java.io.PrintStream;
import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.support.dump.RouteStatDump;
import org.apache.camel.support.dump.StepStatDump;

/**
 * Command to display step information about a Camel route.
 */
public class RouteStepCommand extends AbstractRouteCommand {

    protected static final String HEADER_FORMAT = "%-30s %10s %12s %12s %12s %12s %12s %12s";
    protected static final String OUTPUT_FORMAT = "%-30s %10d %12d %12d %12d %12d %12d %12d";

    private StringEscape stringEscape;
    private volatile String previousCamelContextName;

    public RouteStepCommand(String route, String context) {
        super(route, context);
    }

    /**
     * Sets the {@link StringEscape} to use.
     */
    public void setStringEscape(StringEscape stringEscape) {
        this.stringEscape = stringEscape;
    }

    @Override
    public void executeOnRoute(CamelController camelController, String contextName, String routeId, PrintStream out, PrintStream err) throws Exception {

        JAXBContext context = JAXBContext.newInstance(RouteStatDump.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        // write new header for new camel context
        if (previousCamelContextName == null || !previousCamelContextName.equals(contextName)) {
            out.println("");
            out.println(stringEscape.unescapeJava("\u001B[1mStep\u001B[0m"));
            out.println(stringEscape.unescapeJava("\tCamel Context: " + contextName));
            out.println(String.format(HEADER_FORMAT, "Id", "Count", "Last (ms)", "Delta (ms)", "Mean (ms)", "Min (ms)", "Max (ms)", "Total (ms)"));
        }

        String xml = camelController.getStepStatsAsXml(routeId, contextName, true);
        if (xml != null) {
            RouteStatDump route = (RouteStatDump) unmarshaller.unmarshal(new StringReader(xml));

            long count = route.getExchangesCompleted() + route.getExchangesFailed();
            out.println(String.format(OUTPUT_FORMAT, route.getId(), count, route.getLastProcessingTime(), route.getDeltaProcessingTime(),
                    route.getMeanProcessingTime(), route.getMinProcessingTime(), route.getMaxProcessingTime(), route.getTotalProcessingTime()));

            for (StepStatDump ss : route.getStepStats()) {
                count = ss.getExchangesCompleted() + ss.getExchangesFailed();
                // indent step id with 2 spaces
                out.println(String.format(OUTPUT_FORMAT, "  " + ss.getId(), count, ss.getLastProcessingTime(), ss.getDeltaProcessingTime(),
                        ss.getMeanProcessingTime(), ss.getMinProcessingTime(), ss.getMaxProcessingTime(), ss.getTotalProcessingTime()));
            }
        }

        // we want to group routes from the same context in the same table
        previousCamelContextName = contextName;
    }
}
