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
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.util.CamelContextStatDump;

import static org.apache.camel.util.ObjectHelper.isEmpty;

/**
 * Command to display detailed information about a given {@link org.apache.camel.CamelContext}.
 */
public class ContextInfoCommand extends AbstractContextCommand {

    public static final String XML_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    public static final String OUTPUT_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private StringEscape stringEscape;
    private boolean verbose;

    /**
     * @param context The name of the Camel context
     * @param verbose Whether to output verbose
     */
    public ContextInfoCommand(String context, boolean verbose) {
        super(context);
        this.verbose = verbose;
    }

    /**
     * Sets the {@link org.apache.camel.commands.StringEscape} to use.
     */
    public void setStringEscape(StringEscape stringEscape) {
        this.stringEscape = stringEscape;
    }

    @Override
    protected Object performContextCommand(CamelController camelController, String contextName, PrintStream out, PrintStream err) throws Exception {
        Map<String, Object> row = camelController.getCamelContextInformation(context);
        if (row == null || row.isEmpty()) {
            err.println("Camel context " + context + " not found.");
            return null;
        }

        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mCamel Context " + context + "\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tName: " + row.get("name")));
        out.println(stringEscape.unescapeJava("\tManagementName: " + row.get("managementName")));
        out.println(stringEscape.unescapeJava("\tVersion: " + row.get("version")));
        out.println(stringEscape.unescapeJava("\tStatus: " + row.get("status")));
        out.println(stringEscape.unescapeJava("\tUptime: " + row.get("uptime")));

        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mMiscellaneous\u001B[0m"));
        out.println(stringEscape.unescapeJava("\tSuspended: " + row.get("suspended")));
        out.println(stringEscape.unescapeJava("\tShutdown Timeout: " + row.get("shutdownTimeout") + " sec."));
        if (row.get("managementStatisticsLevel") != null) {
            out.println(stringEscape.unescapeJava("\tManagement StatisticsLevel: " + row.get("managementStatisticsLevel")));
        }
        out.println(stringEscape.unescapeJava("\tAllow UseOriginalMessage: " + row.get("allowUseOriginalMessage")));
        out.println(stringEscape.unescapeJava("\tMessage History: " + row.get("messageHistory")));
        out.println(stringEscape.unescapeJava("\tTracing: " + row.get("tracing")));
        out.println(stringEscape.unescapeJava("\tLog Mask: " + row.get("logMask")));
        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mProperties\u001B[0m"));
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("property.")) {
                key = key.substring(9);
                out.println(stringEscape.unescapeJava("\t" + key + " = " + entry.getValue()));
            }
        }

        if (verbose) {
            out.println("");
            out.println(stringEscape.unescapeJava("\u001B[1mAdvanced\u001B[0m"));
            out.println(stringEscape.unescapeJava("\tClassResolver: " + row.get("classResolver")));
            out.println(stringEscape.unescapeJava("\tPackageScanClassResolver: " + row.get("packageScanClassResolver")));
            out.println(stringEscape.unescapeJava("\tApplicationContextClassLoader: " + row.get("applicationContextClassLoader")));
            out.println(stringEscape.unescapeJava("\tHeadersMapFactory: " + row.get("headersMapFactory")));

            printStatistics(camelController, out);

            // add type converter details
            out.println(stringEscape.unescapeJava("\tNumber of type converters: " + row.get("typeConverter.numberOfTypeConverters")));
            boolean enabled = false;
            if (row.get("typeConverter.statisticsEnabled") != null) {
                enabled = (boolean) row.get("typeConverter.statisticsEnabled");
            }
            if (enabled) {
                long noop = (long) row.get("typeConverter.noopCounter");
                long attempt = (long) row.get("typeConverter.attemptCounter");
                long hit = (long) row.get("typeConverter.hitCounter");
                long miss = (long) row.get("typeConverter.missCounter");
                long failed = (long) row.get("typeConverter.failedCounter");
                out.println(stringEscape.unescapeJava(String.format("\tType converter usage: [noop=%s, attempts=%s, hits=%s, misses=%s, failures=%s]", noop, attempt, hit, miss, failed)));
            }

            // add async processor await details
            out.println(stringEscape.unescapeJava("\tNumber of blocked threads: " + row.get("asyncProcessorAwaitManager.size")));
            enabled = false;
            if (row.get("asyncProcessorAwaitManager.statisticsEnabled") != null) {
                enabled = (boolean) row.get("asyncProcessorAwaitManager.statisticsEnabled");
            }
            if (enabled) {
                long blocked = (long) row.get("asyncProcessorAwaitManager.threadsBlocked");
                long interrupted = (long) row.get("asyncProcessorAwaitManager.threadsInterrupted");
                long total = (long) row.get("asyncProcessorAwaitManager.totalDuration");
                long min = (long) row.get("asyncProcessorAwaitManager.minDuration");
                long max = (long) row.get("asyncProcessorAwaitManager.maxDuration");
                long mean = (long) row.get("asyncProcessorAwaitManager.meanDuration");
                out.println(stringEscape.unescapeJava(String.format("\tAsyncProcessorAwaitManager usage: [blocked=%s, interrupted=%s, total=%s msec, min=%s msec, max=%s msec, mean=%s msec]",
                        blocked, interrupted, total, min, max, mean)));
            }

            // add stream caching details if enabled
            enabled = (boolean) row.get("streamCachingEnabled");
            if (enabled) {
                Object spoolDirectory = safeNull(row.get("streamCaching.spoolDirectory"));
                Object spoolChiper = safeNull(row.get("streamCaching.spoolChiper"));
                Object spoolThreshold = safeNull(row.get("streamCaching.spoolThreshold"));
                Object spoolUsedHeapMemoryThreshold = safeNull(row.get("streamCaching.spoolUsedHeapMemoryThreshold"));
                Object spoolUsedHeapMemoryLimit = safeNull(row.get("streamCaching.spoolUsedHeapMemoryLimit"));
                Object anySpoolRules = safeNull(row.get("streamCaching.anySpoolRules"));
                Object bufferSize = safeNull(row.get("streamCaching.bufferSize"));
                Object removeSpoolDirectoryWhenStopping = safeNull(row.get("streamCaching.removeSpoolDirectoryWhenStopping"));
                boolean statisticsEnabled = (boolean) row.get("streamCaching.statisticsEnabled");

                String text = String.format("\tStream caching: [spoolDirectory=%s, spoolChiper=%s, spoolThreshold=%s, spoolUsedHeapMemoryThreshold=%s, "
                                + "spoolUsedHeapMemoryLimit=%s, anySpoolRules=%s, bufferSize=%s, removeSpoolDirectoryWhenStopping=%s, statisticsEnabled=%s]",
                        spoolDirectory, spoolChiper, spoolThreshold, spoolUsedHeapMemoryThreshold, spoolUsedHeapMemoryLimit, anySpoolRules, bufferSize,
                        removeSpoolDirectoryWhenStopping, statisticsEnabled);
                out.println(stringEscape.unescapeJava(text));

                if (statisticsEnabled) {
                    Object cacheMemoryCounter = safeNull(row.get("streamCaching.cacheMemoryCounter"));
                    Object cacheMemorySize = safeNull(row.get("streamCaching.cacheMemorySize"));
                    Object cacheMemoryAverageSize = safeNull(row.get("streamCaching.cacheMemoryAverageSize"));
                    Object cacheSpoolCounter = safeNull(row.get("streamCaching.cacheSpoolCounter"));
                    Object cacheSpoolSize = safeNull(row.get("streamCaching.cacheSpoolSize"));
                    Object cacheSpoolAverageSize = safeNull(row.get("streamCaching.cacheSpoolAverageSize"));

                    text = String.format("\t                       [cacheMemoryCounter=%s, cacheMemorySize=%s, cacheMemoryAverageSize=%s, cacheSpoolCounter=%s, "
                            + "cacheSpoolSize=%s, cacheSpoolAverageSize=%s]",
                            cacheMemoryCounter, cacheMemorySize, cacheMemoryAverageSize, cacheSpoolCounter, cacheSpoolSize, cacheSpoolAverageSize);
                    out.println(stringEscape.unescapeJava(text));
                }
            }

            long totalRoutes = (long) row.get("totalRoutes");
            long startedRoutes = (long) row.get("totalRoutes");
            out.println(stringEscape.unescapeJava("\tNumber of running routes: " + startedRoutes));
            out.println(stringEscape.unescapeJava("\tNumber of not running routes: " + (totalRoutes - startedRoutes)));
        }

        return null;
    }

    protected void printStatistics(CamelController camelController, PrintStream out) throws Exception {
        out.println("");
        out.println(stringEscape.unescapeJava("\u001B[1mStatistics\u001B[0m"));

        String xml = camelController.getCamelContextStatsAsXml(context, true, false);
        if (xml != null) {
            JAXBContext context = JAXBContext.newInstance(CamelContextStatDump.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            CamelContextStatDump stat = (CamelContextStatDump) unmarshaller.unmarshal(new StringReader(xml));

            long total = stat.getExchangesCompleted() + stat.getExchangesFailed();
            out.println(stringEscape.unescapeJava("\tExchanges Total: " + total));
            out.println(stringEscape.unescapeJava("\tExchanges Completed: " + stat.getExchangesCompleted()));
            out.println(stringEscape.unescapeJava("\tExchanges Failed: " + stat.getExchangesFailed()));
            out.println(stringEscape.unescapeJava("\tExchanges Inflight: " + stat.getExchangesInflight()));
            out.println(stringEscape.unescapeJava("\tMin Processing Time: " + stat.getMinProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tMax Processing Time: " + stat.getMaxProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tMean Processing Time: " + stat.getMeanProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tTotal Processing Time: " + stat.getTotalProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tLast Processing Time: " + stat.getLastProcessingTime() + " ms"));
            out.println(stringEscape.unescapeJava("\tDelta Processing Time: " + stat.getDeltaProcessingTime() + " ms"));

            if (isEmpty(stat.getStartTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tStart Statistics Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(stat.getStartTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tStart Statistics Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (isEmpty(stat.getResetTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tReset Statistics Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(stat.getResetTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tReset Statistics Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (isEmpty(stat.getFirstExchangeCompletedTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tFirst Exchange Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(stat.getFirstExchangeCompletedTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tFirst Exchange Date: " + text));
            }

            // Test for null to see if a any exchanges have been processed first to avoid NPE
            if (isEmpty(stat.getLastExchangeCompletedTimestamp())) {
                // Print an empty value for scripting
                out.println(stringEscape.unescapeJava("\tLast Exchange Date:"));
            } else {
                Date date = new SimpleDateFormat(XML_TIMESTAMP_FORMAT).parse(stat.getLastExchangeCompletedTimestamp());
                String text = new SimpleDateFormat(OUTPUT_TIMESTAMP_FORMAT).format(date);
                out.println(stringEscape.unescapeJava("\tLast Exchange Date: " + text));
            }
        }

    }

}
