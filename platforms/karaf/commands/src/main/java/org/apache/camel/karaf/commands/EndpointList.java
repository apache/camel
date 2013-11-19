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
package org.apache.camel.karaf.commands;

import java.io.PrintStream;
import java.net.URLDecoder;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.ServiceStatus;
import org.apache.camel.StatefulService;
import org.apache.camel.util.URISupport;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;

/**
 * List the Camel endpoints available in the Karaf instance.
 */
@Command(scope = "camel", name = "endpoint-list", description = "Lists all Camel endpoints available in CamelContexts.")
public class EndpointList extends OsgiCommandSupport {

    private static final String CONTEXT_COLUMN_LABEL = "Context";
    private static final String URI_COLUMN_LABEL = "Uri";
    private static final String STATUS_COLUMN_LABEL = "Status";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = "[ ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ]";
    private static final String DEFAULT_HEADER_PREAMBLE = "  ";
    private static final String DEFAULT_HEADER_POSTAMBLE = "  ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // endpoint uris can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;

    @Argument(index = 0, name = "name", description = "The Camel context name where to look for the endpoints", required = false, multiValued = false)
    String name;

    @Option(name = "--decode", aliases = "-d", description = "Whether to decode the endpoint uri so its human readable",
            required = false, multiValued = false, valueToShowInHelp = "true")
    Boolean decode;

    @Option(name = "--verbose", aliases = "-v", description = "Verbose output which does not limit the length of the uri shown",
            required = false, multiValued = false, valueToShowInHelp = "false")
    Boolean verbose;

    private CamelController camelController;

    public void setCamelController(CamelController camelController) {
        this.camelController = camelController;
    }

    protected Object doExecute() throws Exception {
        List<Endpoint> endpoints = camelController.getEndpoints(name);

        final Map<String, Integer> columnWidths = computeColumnWidths(endpoints);
        final String headerFormat = buildFormatString(columnWidths, true);
        final String rowFormat = buildFormatString(columnWidths, false);
        final PrintStream out = System.out;

        if (endpoints.size() > 0) {
            out.println(String.format(headerFormat, CONTEXT_COLUMN_LABEL, URI_COLUMN_LABEL, STATUS_COLUMN_LABEL));
            for (final Endpoint endpoint : endpoints) {
                String contextId = endpoint.getCamelContext().getName();
                String uri = endpoint.getEndpointUri();
                if (decode == null || decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);
                String state = getEndpointState(endpoint);
                out.println(String.format(rowFormat, contextId, uri, state));
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(final Iterable<Endpoint> endpoints) throws Exception {
        if (endpoints == null) {
            throw new IllegalArgumentException("Unable to determine column widths from null Iterable<Endpoint>");
        } else {
            int maxContextLen = 0;
            int maxUriLen = 0;
            int maxStatusLen = 0;

            for (final Endpoint endpoint : endpoints) {
                final String name = endpoint.getCamelContext().getName();
                maxContextLen = java.lang.Math.max(maxContextLen, name == null ? 0 : name.length());

                String uri = endpoint.getEndpointUri();
                if (decode == null || decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);

                maxUriLen = java.lang.Math.max(maxUriLen, uri == null ? 0 : uri.length());

                final String status = getEndpointState(endpoint);
                maxStatusLen = java.lang.Math.max(maxStatusLen, status == null ? 0 : status.length());
            }

            final Map<String, Integer> retval = new Hashtable<String, Integer>(3);
            retval.put(CONTEXT_COLUMN_LABEL, maxContextLen);
            retval.put(URI_COLUMN_LABEL, maxUriLen);
            retval.put(STATUS_COLUMN_LABEL, maxStatusLen);

            return retval;
        }
    }

    private String buildFormatString(final Map<String, Integer> columnWidths, final boolean isHeader) {
        final String fieldPreamble;
        final String fieldPostamble;
        final int columnWidthIncrement;

        if (isHeader) {
            fieldPreamble = DEFAULT_HEADER_PREAMBLE;
            fieldPostamble = DEFAULT_HEADER_POSTAMBLE;
        } else {
            fieldPreamble = DEFAULT_FIELD_PREAMBLE;
            fieldPostamble = DEFAULT_FIELD_POSTAMBLE;
        }
        columnWidthIncrement = DEFAULT_COLUMN_WIDTH_INCREMENT;

        final int contextLen = java.lang.Math.min(columnWidths.get(CONTEXT_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        final int uriLen = java.lang.Math.min(columnWidths.get(URI_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        final int statusLen = java.lang.Math.min(columnWidths.get(STATUS_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(contextLen).append('.').append(contextLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uriLen).append('.').append(uriLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(statusLen).append('.').append(statusLen).append('s').append(fieldPostamble).append(' ');

        return retval.toString();
    }

    private int getMaxColumnWidth() {
        if (verbose != null && verbose) {
            return Integer.MAX_VALUE;
        } else {
            return MAX_COLUMN_WIDTH;
        }
    }

    private static String getEndpointState(Endpoint endpoint) {
        // must use String type to be sure remote JMX can read the attribute without requiring Camel classes.
        if (endpoint instanceof StatefulService) {
            ServiceStatus status = ((StatefulService) endpoint).getStatus();
            return status.name();
        }

        // assume started if not a ServiceSupport instance
        return ServiceStatus.Started.name();
    }

}
