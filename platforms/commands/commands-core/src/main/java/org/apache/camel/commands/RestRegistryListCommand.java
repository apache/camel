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
import java.net.URLDecoder;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.URISupport;

/**
 * List the Camel REST services from the Rest registry available in the JVM.
 */
public class RestRegistryListCommand extends AbstractContextCommand {

    private static final String URL_COLUMN_NAME = "Url";
    private static final String BASE_PATH_LABEL = "Base Path";
    private static final String URI_TEMPLATE_LABEL = "Uri Template";
    private static final String METHOD_COLUMN_LABEL = "Method";
    private static final String STATE_COLUMN_LABEL = "State";
    private static final String ROUTE_COLUMN_LABEL = "Route Id";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // endpoint uris can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;

    boolean decode = true;
    boolean verbose;

    public RestRegistryListCommand(String context, boolean decode, boolean verbose) {
        super(context);
        this.decode = decode;
        this.verbose = verbose;
    }

    @Override
    protected Object performContextCommand(CamelController camelController, String contextName, PrintStream out, PrintStream err) throws Exception {
        List<Map<String, String>> services = camelController.getRestServices(contextName);
        if (services.isEmpty()) {
            out.print("There are no REST services");
            return null;
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(services);
        final String headerFormat = buildFormatString(columnWidths, true, verbose);
        final String rowFormat = buildFormatString(columnWidths, false, verbose);

        if (services.size() > 0) {
            if (verbose) {
                out.println(String.format(headerFormat, URL_COLUMN_NAME, BASE_PATH_LABEL, URI_TEMPLATE_LABEL, METHOD_COLUMN_LABEL, STATE_COLUMN_LABEL, ROUTE_COLUMN_LABEL));
                out.println(String.format(headerFormat, "---", "---------", "------------", "------", "-----", "--------"));
            } else {
                out.println(String.format(headerFormat, BASE_PATH_LABEL, URI_TEMPLATE_LABEL, METHOD_COLUMN_LABEL, STATE_COLUMN_LABEL, ROUTE_COLUMN_LABEL));
                out.println(String.format(headerFormat, "---------", "------------", "------", "-----", "--------"));
            }
            for (Map<String, String> row : services) {
                String uri = null;
                if (verbose) {
                    uri = row.get("url");
                    if (decode) {
                        // decode uri so its more human readable
                        uri = URLDecoder.decode(uri, "UTF-8");
                    }
                    // sanitize and mask uri so we dont see passwords
                    uri = URISupport.sanitizeUri(uri);
                }
                String basePath = row.get("basePath");
                String uriTemplate = row.get("uriTemplate") != null ? row.get("uriTemplate") : "";
                String method = row.get("method");
                String state = row.get("state");
                String route = row.get("routeId");
                if (verbose) {
                    out.println(String.format(rowFormat, uri, basePath, uriTemplate, method, state, route));
                } else {
                    out.println(String.format(rowFormat, basePath, uriTemplate, method, state, route));
                }
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(List<Map<String, String>> services) throws Exception {
        int maxUriLen = 0;
        int maxBasePathLen = 0;
        int maxUriTemplateLen = 0;
        int maxMethodLen = 0;
        int maxStatusLen = 0;
        int maxRouteLen = 0;

        for (Map<String, String> row : services) {
            String uri = row.get("url");
            if (decode) {
                // decode uri so its more human readable
                uri = URLDecoder.decode(uri, "UTF-8");
            }
            // sanitize and mask uri so we dont see passwords
            uri = URISupport.sanitizeUri(uri);
            maxUriLen = Math.max(maxUriLen, uri == null ? 0 : uri.length());

            String basePath = row.get("basePath");
            maxBasePathLen = Math.max(maxBasePathLen, basePath == null ? 0 : basePath.length());

            String uriTemplate = row.get("uriTemplate");
            maxUriTemplateLen = Math.max(maxUriTemplateLen, uriTemplate == null ? 0 : uriTemplate.length());

            String method = row.get("method");
            maxMethodLen = Math.max(maxMethodLen, method == null ? 0 : method.length());

            String status = row.get("state");
            maxStatusLen = Math.max(maxStatusLen, status == null ? 0 : status.length());

            String routeId = row.get("routeId");
            maxRouteLen = Math.max(maxRouteLen, routeId == null ? 0 : routeId.length());
        }

        final Map<String, Integer> retval = new Hashtable<String, Integer>();
        retval.put(URL_COLUMN_NAME, maxUriLen);
        retval.put(BASE_PATH_LABEL, maxBasePathLen);
        retval.put(URI_TEMPLATE_LABEL, maxUriTemplateLen);
        retval.put(METHOD_COLUMN_LABEL, maxMethodLen);
        retval.put(STATE_COLUMN_LABEL, maxStatusLen);
        retval.put(ROUTE_COLUMN_LABEL, maxRouteLen);

        return retval;
    }

    private String buildFormatString(final Map<String, Integer> columnWidths, final boolean isHeader, final boolean isVerbose) {
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

        int uriLen = Math.min(columnWidths.get(URL_COLUMN_NAME) + columnWidthIncrement, getMaxColumnWidth());
        int basePathLen = Math.min(columnWidths.get(BASE_PATH_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int uriTemplateLen = Math.min(columnWidths.get(URI_TEMPLATE_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int methodLen = Math.min(columnWidths.get(METHOD_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int statusLen = Math.min(columnWidths.get(STATE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int routeLen = Math.min(columnWidths.get(ROUTE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        uriLen = Math.max(MIN_COLUMN_WIDTH, uriLen);
        basePathLen = Math.max(MIN_COLUMN_WIDTH, basePathLen);
        uriTemplateLen = Math.max(MIN_COLUMN_WIDTH, uriTemplateLen);
        methodLen = Math.max(MIN_COLUMN_WIDTH, methodLen);
        statusLen = Math.max(MIN_COLUMN_WIDTH, statusLen);

        // last row does not have min width

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        if (isVerbose) {
            retval.append(fieldPreamble).append("%-").append(uriLen).append('.').append(uriLen).append('s').append(fieldPostamble).append(' ');
        }
        retval.append(fieldPreamble).append("%-").append(basePathLen).append('.').append(basePathLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uriTemplateLen).append('.').append(uriTemplateLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(methodLen).append('.').append(methodLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(statusLen).append('.').append(statusLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(routeLen).append('.').append(routeLen).append('s').append(fieldPostamble).append(' ');

        return retval.toString();
    }

    private int getMaxColumnWidth() {
        if (verbose) {
            return Integer.MAX_VALUE;
        } else {
            return MAX_COLUMN_WIDTH;
        }
    }
}
