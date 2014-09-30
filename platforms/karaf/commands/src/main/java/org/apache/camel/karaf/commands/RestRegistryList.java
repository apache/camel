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

import org.apache.camel.spi.RestRegistry;
import org.apache.camel.util.URISupport;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;

/**
 * List the Camel REST services from the Rest registry available in the Karaf instance.
 */
@Command(scope = "camel", name = "rest-registry-list", description = "Lists all Camel REST services enlisted in the Rest Registry from all CamelContexts.")
public class RestRegistryList extends CamelCommandSupport {

    private static final String CONTEXT_COLUMN_LABEL = "Context";
    private static final String URL_COLUMN_NAME = "Url";
    private static final String BASE_PATH_LABEL = "Base Path";
    private static final String URI_TEMPLATE_LABEL = "Uri Template";
    private static final String METHOD_COLUMN_LABEL = "Method";
    private static final String STATE_COLUMN_LABEL = "State";

    private static final int DEFAULT_COLUMN_WIDTH_INCREMENT = 0;
    private static final String DEFAULT_FIELD_PREAMBLE = " ";
    private static final String DEFAULT_FIELD_POSTAMBLE = " ";
    private static final String DEFAULT_HEADER_PREAMBLE = " ";
    private static final String DEFAULT_HEADER_POSTAMBLE = " ";
    private static final int DEFAULT_FORMAT_BUFFER_LENGTH = 24;
    // endpoint uris can be very long so clip by default after 120 chars
    private static final int MAX_COLUMN_WIDTH = 120;
    private static final int MIN_COLUMN_WIDTH = 12;

    @Argument(index = 0, name = "name", description = "The Camel context name where to look for the REST services", required = false, multiValued = false)
    String name;

    @Option(name = "--decode", aliases = "-d", description = "Whether to decode the endpoint uri so its human readable",
            required = false, multiValued = false, valueToShowInHelp = "true")
    Boolean decode = true;

    @Option(name = "--verbose", aliases = "-v", description = "Verbose output which does not limit the length of the uri shown",
            required = false, multiValued = false, valueToShowInHelp = "false")
    Boolean verbose = false;

    protected Object doExecute() throws Exception {
        Map<String, List<RestRegistry.RestService>> services = camelController.getRestServices(name);
        if (services.isEmpty()) {
            System.out.print("There are no REST services");
            return null;
        }

        final Map<String, Integer> columnWidths = computeColumnWidths(services);
        final String headerFormat = buildFormatString(columnWidths, true, verbose);
        final String rowFormat = buildFormatString(columnWidths, false, verbose);
        final PrintStream out = System.out;

        if (services.size() > 0) {
            if (verbose) {
                out.println(String.format(headerFormat, CONTEXT_COLUMN_LABEL, URL_COLUMN_NAME, BASE_PATH_LABEL, URI_TEMPLATE_LABEL, METHOD_COLUMN_LABEL, STATE_COLUMN_LABEL));
                out.println(String.format(headerFormat, "-------", "---", "---------", "------------", "------", "-----"));
            } else {
                out.println(String.format(headerFormat, CONTEXT_COLUMN_LABEL, BASE_PATH_LABEL, URI_TEMPLATE_LABEL, METHOD_COLUMN_LABEL, STATE_COLUMN_LABEL));
                out.println(String.format(headerFormat, "-------", "---------", "------------", "------", "-----"));
            }
            for (Map.Entry<String, List<RestRegistry.RestService>> entry : services.entrySet()) {
                String contextName = entry.getKey();
                for (final RestRegistry.RestService service : entry.getValue()) {
                    String contextId = contextName;

                    String uri = null;
                    if (verbose) {
                        uri = service.getUrl();
                        if (decode == null || decode) {
                            // decode uri so its more human readable
                            uri = URLDecoder.decode(uri, "UTF-8");
                        }
                        // sanitize and mask uri so we dont see passwords
                        uri = URISupport.sanitizeUri(uri);
                    }
                    String basePath = service.getBasePath();
                    String uriTemplate = service.getUriTemplate() != null ? service.getUriTemplate() : "";
                    String method = service.getMethod();
                    String state = service.getState();
                    if (verbose) {
                        out.println(String.format(rowFormat, contextId, uri, basePath, uriTemplate, method, state));
                    } else {
                        out.println(String.format(rowFormat, contextId, basePath, uriTemplate, method, state));
                    }
                }
            }
        }

        return null;
    }

    private Map<String, Integer> computeColumnWidths(Map<String, List<RestRegistry.RestService>> services) throws Exception {
        int maxContextLen = 0;
        int maxUriLen = 0;
        int maxBasePathLen = 0;
        int maxUriTemplateLen = 0;
        int maxMethodLen = 0;
        int maxStatusLen = 0;

        for (Map.Entry<String, List<RestRegistry.RestService>> entry : services.entrySet()) {
            String contextName = entry.getKey();
            for (final RestRegistry.RestService service : entry.getValue()) {
                maxContextLen = Math.max(maxContextLen, contextName == null ? 0 : contextName.length());

                String uri = service.getUrl();
                if (decode == null || decode) {
                    // decode uri so its more human readable
                    uri = URLDecoder.decode(uri, "UTF-8");
                }
                // sanitize and mask uri so we dont see passwords
                uri = URISupport.sanitizeUri(uri);
                maxUriLen = Math.max(maxUriLen, uri == null ? 0 : uri.length());

                String basePath = service.getBasePath();
                maxBasePathLen = Math.max(maxBasePathLen, basePath == null ? 0 : basePath.length());

                String uriTemplate = service.getUriTemplate();
                maxUriTemplateLen = Math.max(maxUriTemplateLen, uriTemplate == null ? 0 : uriTemplate.length());

                String method = service.getMethod();
                maxMethodLen = Math.max(maxMethodLen, method == null ? 0 : method.length());

                String status = service.getState();
                maxStatusLen = Math.max(maxStatusLen, status == null ? 0 : status.length());
            }
        }

        final Map<String, Integer> retval = new Hashtable<String, Integer>(6);
        retval.put(CONTEXT_COLUMN_LABEL, maxContextLen);
        retval.put(URL_COLUMN_NAME, maxUriLen);
        retval.put(BASE_PATH_LABEL, maxBasePathLen);
        retval.put(URI_TEMPLATE_LABEL, maxUriTemplateLen);
        retval.put(METHOD_COLUMN_LABEL, maxMethodLen);
        retval.put(STATE_COLUMN_LABEL, maxStatusLen);

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

        int contextLen = Math.min(columnWidths.get(CONTEXT_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int uriLen = Math.min(columnWidths.get(URL_COLUMN_NAME) + columnWidthIncrement, getMaxColumnWidth());
        int basePathLen = Math.min(columnWidths.get(BASE_PATH_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int uriTemplateLen = Math.min(columnWidths.get(URI_TEMPLATE_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int methodLen = Math.min(columnWidths.get(METHOD_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        int statusLen = Math.min(columnWidths.get(STATE_COLUMN_LABEL) + columnWidthIncrement, getMaxColumnWidth());
        contextLen = Math.max(MIN_COLUMN_WIDTH, contextLen);
        basePathLen = Math.max(MIN_COLUMN_WIDTH, basePathLen);
        uriLen = Math.max(MIN_COLUMN_WIDTH, uriLen);
        uriTemplateLen = Math.max(MIN_COLUMN_WIDTH, uriTemplateLen);
        methodLen = Math.max(MIN_COLUMN_WIDTH, methodLen);

        // last row does not have min width

        final StringBuilder retval = new StringBuilder(DEFAULT_FORMAT_BUFFER_LENGTH);
        retval.append(fieldPreamble).append("%-").append(contextLen).append('.').append(contextLen).append('s').append(fieldPostamble).append(' ');
        if (isVerbose) {
            retval.append(fieldPreamble).append("%-").append(uriLen).append('.').append(uriLen).append('s').append(fieldPostamble).append(' ');
        }
        retval.append(fieldPreamble).append("%-").append(basePathLen).append('.').append(basePathLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(uriTemplateLen).append('.').append(uriTemplateLen).append('s').append(fieldPostamble).append(' ');
        retval.append(fieldPreamble).append("%-").append(methodLen).append('.').append(methodLen).append('s').append(fieldPostamble).append(' ');
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

}
