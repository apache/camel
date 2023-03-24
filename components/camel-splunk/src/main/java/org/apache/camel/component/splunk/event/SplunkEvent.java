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
package org.apache.camel.component.splunk.event;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class SplunkEvent implements Serializable {

    // ----------------------------------
    // Common event fields
    // ----------------------------------

    /**
     * A device-specific classification provided as part of the event.
     */
    public static final String COMMON_CATEGORY = "category";
    /**
     * A device-specific classification provided as part of the event.
     */
    public static final String COMMON_COUNT = "count";
    /**
     * The free-form description of a particular event.
     */
    public static final String COMMON_DESC = "desc";
    /**
     * The name of a given DHCP pool on a DHCP server.
     */
    public static final String COMMON_DHCP_POOL = "dhcp_pool";
    /**
     * The amount of time the event lasted.
     */
    public static final String COMMON_DURATION = "duration";
    /**
     * The fully qualified domain name of the device transmitting or recording the log record.
     */
    public static final String COMMON_DVC_HOST = "dvc_host";
    /**
     * The IPv4 address of the device reporting the event.
     */
    public static final String COMMON_DVC_IP = "dvc_ip";
    /**
     * The IPv6 address of the device reporting the event.
     */
    public static final String COMMON_DVC_IP6 = "dvc_ip6";
    /**
     * The free-form description of the device's physical location.
     */
    public static final String COMMON_DVC_LOCATION = "dvc_location";
    /**
     * The MAC (layer 2) address of the device reporting the event.
     */
    public static final String COMMON_DVC_MAC = "dvc_mac";
    /**
     * The Windows NT domain of the device recording or transmitting the event.
     */
    public static final String COMMON_DVC_NT_DOMAIN = "dvc_nt_domain";
    /**
     * The Windows NT host name of the device recording or transmitting the event.
     */
    public static final String COMMON_DVC_NT_HOST = "dvc_nt_host";
    /**
     * Time at which the device recorded the event.
     */
    public static final String COMMON_DVC_TIME = "dvc_time";
    /**
     * The event's specified end time.
     */
    public static final String COMMON_END_TIME = "end_time";
    /**
     * A unique identifier that identifies the event. This is unique to the reporting device.
     */
    public static final String COMMON_EVENT_ID = "event_id";
    /**
     * The length of the datagram, event, message, or packet.
     */
    public static final String COMMON_LENGTH = "length";
    /**
     * The log-level that was set on the device and recorded in the event.
     */
    public static final String COMMON_LOG_LEVEL = "log_level";
    /**
     * The name of the event as reported by the device. The name should not contain information that's already being
     * parsed into other fields from the event, such as IP addresses.
     */
    public static final String COMMON_NAME = "name";
    /**
     * An integer assigned by the device operating system to the process creating the record.
     */
    public static final String COMMON_PID = "pid";
    /**
     * An environment-specific assessment of the event's importance, based on elements such as event severity, business
     * function of the affected system, or other locally defined variables.
     */
    public static final String COMMON_PRIORITY = "priority";
    /**
     * The product that generated the event.
     */
    public static final String COMMON_PRODUCT = "product";
    /**
     * The version of the product that generated the event.
     */
    public static final String COMMON_PRODUCT_VERSION = "product_version";
    /**
     * The result root cause, such as connection refused, timeout, crash, and so on.
     */
    public static final String COMMON_REASON = "reason";
    /**
     * The action result. Often is a binary choice: succeeded and failed, allowed and denied, and so on.
     */
    public static final String COMMON_RESULT = "result";
    /**
     * The severity (or priority) of an event as reported by the originating device.
     */
    public static final String COMMON_SEVERITY = "severity";
    /**
     * The event's specified start time.
     */
    public static final String COMMON_START_TIME = "start_time";
    /**
     * The transaction identifier.
     */
    public static final String COMMON_TRANSACTION_ID = "transaction_id";
    /**
     * A uniform record locator (a web address, in other words) included in a record.
     */
    public static final String COMMON_URL = "url";
    /**
     * The vendor who made the product that generated the event.
     */
    public static final String COMMON_VENDOR = "vendor";

    /**
     * Event break delimiter
     */
    public static final String LINEBREAK = "\n";
    // ----------------------------------
    // Update
    // ----------------------------------

    /**
     * The name of the installed update.
     */
    public static final String UPDATE_PACKAGE = "package";

    /**
     * default key value delimiter
     */
    private static final String KVDELIM = "=";
    /**
     * default pair delimiter
     */
    private static final String PAIRDELIM = " ";
    /**
     * default quote char
     */
    private static final char QUOTE = '"';
    /**
     * default date format is using internal generated date
     */
    private static final String DATEFORMATPATTERN = "yyyy-MM-dd\tHH:mm:ss:SSSZ";
    /**
     * Date Formatter
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern(DATEFORMATPATTERN);

    /**
     * Event prefix fields
     */
    private static final String PREFIX_NAME = "name";
    private static final String PREFIX_EVENT_ID = "event_id";

    /**
     * Java Throwable type fields
     */
    private static final String THROWABLE_CLASS = "throwable_class";
    private static final String THROWABLE_MESSAGE = "throwable_message";
    private static final String THROWABLE_STACKTRACE_ELEMENTS = "stacktrace_elements";

    private static final long serialVersionUID = 1L;

    /**
     * Whether or not to put quotes around values
     */
    private boolean quoteValues = true;

    /**
     * Whether or not to add a date to the event string
     */
    private boolean useInternalDate = true;

    /**
     * Contents of the event message
     */
    private Map<String, String> event;

    /**
     * A Constructor to load data from a Map
     *
     * @param data the map
     */
    public SplunkEvent(Map<String, String> data) {
        this.event = data;
    }

    /**
     * A Copy constructor
     */
    public SplunkEvent(SplunkEvent splunkEvent) {
        this.event = splunkEvent.getEventData();
        this.quoteValues = splunkEvent.quoteValues;
        this.useInternalDate = splunkEvent.useInternalDate;
    }

    /**
     * Constructor to create a generic event
     *
     * @param eventName       the event name
     * @param eventID         the event id
     * @param useInternalDate whether or not to add a date to the event string
     * @param quoteValues     whether or not to put quotes around values
     */
    public SplunkEvent(String eventName, String eventID, boolean useInternalDate, boolean quoteValues) {
        this.event = new LinkedHashMap<>();
        this.quoteValues = quoteValues;
        this.useInternalDate = useInternalDate;

        addPair(PREFIX_NAME, eventName);
        if (eventID != null) {
            addPair(PREFIX_EVENT_ID, eventID);
        }

    }

    /**
     * Constructor to create a generic event with the default format
     *
     * @param eventName the event name
     * @param eventID   the event ID
     */
    public SplunkEvent(String eventName, String eventID) {
        this(eventName, eventID, true, true);
    }

    /**
     * Default constructor
     */
    public SplunkEvent() {
        this.event = new LinkedHashMap<>();
    }

    public Map<String, String> getEventData() {
        return event;
    }

    /**
     * Add a key value pair
     */
    public void addPair(String key, char value) {
        addPair(key, String.valueOf(value));
    }

    /**
     * Add a key value pair
     */
    public void addPair(String key, boolean value) {
        addPair(key, String.valueOf(value));
    }

    /**
     * Add a key value pair
     */
    public void addPair(String key, double value) {
        addPair(key, String.valueOf(value));
    }

    /**
     * Add a key value pair
     */
    public void addPair(String key, long value) {
        addPair(key, String.valueOf(value));
    }

    /**
     * Add a key value pair
     */
    public void addPair(String key, int value) {
        addPair(key, String.valueOf(value));
    }

    /**
     * Add a key value pair
     */
    public void addPair(String key, Object value) {
        addPair(key, value.toString());
    }

    /**
     * Utility method for formatting Throwable,Error,Exception objects in a more linear and Splunk friendly manner than
     * printStackTrace
     *
     * @param throwable the Throwable object to add to the event
     */
    public void addThrowable(Throwable throwable) {
        addThrowableObject(throwable, -1);
    }

    /**
     * Utility method for formatting Throwable,Error,Exception objects in a more linear and Splunk friendly manner than
     * printStackTrace
     *
     * @param throwable       the Throwable object to add to the event
     * @param stackTraceDepth maximum number of stacktrace elements to log
     */
    public void addThrowable(Throwable throwable, int stackTraceDepth) {
        addThrowableObject(throwable, stackTraceDepth);
    }

    /**
     * Internal private method for formatting Throwable,Error,Exception objects in a more linear and Splunk friendly
     * manner than printStackTrace
     *
     * @param throwable       the Throwable object to add to the event
     * @param stackTraceDepth maximum number of stacktrace elements to log, -1 for all
     */

    private void addThrowableObject(Throwable throwable, int stackTraceDepth) {
        addPair(THROWABLE_CLASS, throwable.getClass().getCanonicalName());
        addPair(THROWABLE_MESSAGE, throwable.getMessage());
        StackTraceElement[] elements = throwable.getStackTrace();
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (StackTraceElement element : elements) {
            depth++;
            if (stackTraceDepth == -1 || stackTraceDepth >= depth) {
                sb.append(element.toString()).append(",");
            } else {
                break;
            }
        }
        addPair(THROWABLE_STACKTRACE_ELEMENTS, sb.toString());
    }

    /**
     * Add a key value pair
     */
    public void addPair(String key, String value) {
        this.event.put(key, value);
    }

    /**
     * return the completed event message
     */
    @Override
    public String toString() {
        StringBuilder event = new StringBuilder();
        if (useInternalDate) {
            event.append(DATE_FORMATTER.print(new Date().getTime())).append(PAIRDELIM);
        }
        for (Map.Entry<String, String> eventEntry : this.event.entrySet()) {
            event.append(eventEntry.getKey());
            event.append(KVDELIM);
            if (quoteValues) {
                event.append(QUOTE).append(eventEntry.getValue()).append(QUOTE).append(PAIRDELIM);
            } else {
                event.append(eventEntry.getValue()).append(PAIRDELIM);
            }
        }
        // trim off trailing pair delim char(s)
        return event.substring(0, event.length() - PAIRDELIM.length()) + LINEBREAK;
    }

    public void setCommonCategory(String commonCategory) {
        addPair(COMMON_CATEGORY, commonCategory);
    }

    public void setCommonCount(String commonCount) {
        addPair(COMMON_COUNT, commonCount);
    }

    public void setCommonDesc(String commonDesc) {
        addPair(COMMON_DESC, commonDesc);
    }

    public void setCommonDhcpPool(String commonDhcpPool) {
        addPair(COMMON_DHCP_POOL, commonDhcpPool);
    }

    public void setCommonDuration(long commonDuration) {
        addPair(COMMON_DURATION, commonDuration);
    }

    public void setCommonDvcHost(String commonDvcHost) {
        addPair(COMMON_DVC_HOST, commonDvcHost);
    }

    public void setCommonDvcIp(String commonDvcIp) {
        addPair(COMMON_DVC_IP, commonDvcIp);
    }

    public void setCommonDvcIp6(String commonDvcIp6) {
        addPair(COMMON_DVC_IP6, commonDvcIp6);
    }

    public void setCommonDvcLocation(String commonDvcLocation) {
        addPair(COMMON_DVC_LOCATION, commonDvcLocation);
    }

    public void setCommonDvcMac(String commonDvcMac) {
        addPair(COMMON_DVC_MAC, commonDvcMac);
    }

    public void setCommonDvcNtDomain(String commonDvcNtDomain) {
        addPair(COMMON_DVC_NT_DOMAIN, commonDvcNtDomain);
    }

    public void setCommonDvcNtHost(String commonDvcNtHost) {
        addPair(COMMON_DVC_NT_HOST, commonDvcNtHost);
    }

    public void setCommonDvcTime(long commonDvcTime) {
        addPair(COMMON_DVC_TIME, commonDvcTime);
    }

    public void setCommonEndTime(long commonEndTime) {
        addPair(COMMON_END_TIME, commonEndTime);
    }

    public void setCommonEventId(long commonEventId) {
        addPair(COMMON_EVENT_ID, commonEventId);
    }

    public void setCommonLength(long commonLength) {
        addPair(COMMON_LENGTH, commonLength);
    }

    public void setCommonLogLevel(String commonLogLevel) {
        addPair(COMMON_LOG_LEVEL, commonLogLevel);
    }

    public void setCommonName(String commonName) {
        addPair(COMMON_NAME, commonName);
    }

    public void setCommonPid(long commonPid) {
        addPair(COMMON_PID, commonPid);
    }

    public void setCommonPriority(long commonPriority) {
        addPair(COMMON_PRIORITY, commonPriority);
    }

    public void setCommonProduct(String commonProduct) {
        addPair(COMMON_PRODUCT, commonProduct);
    }

    public void setCommonProductVersion(long commonProductVersion) {
        addPair(COMMON_PRODUCT_VERSION, commonProductVersion);
    }

    public void setCommonReason(String commonReason) {
        addPair(COMMON_REASON, commonReason);
    }

    public void setCommonResult(String commonResult) {
        addPair(COMMON_RESULT, commonResult);
    }

    public void setCommonSeverity(String commonSeverity) {
        addPair(COMMON_SEVERITY, commonSeverity);
    }

    public void setCommonStartTime(long commonStartTime) {
        addPair(COMMON_START_TIME, commonStartTime);
    }

    public void setCommonTransactionId(String commonTransactionId) {
        addPair(COMMON_TRANSACTION_ID, commonTransactionId);
    }

    public void setCommonUrl(String commonUrl) {
        addPair(COMMON_URL, commonUrl);
    }

    public void setCommonVendor(String commonVendor) {
        addPair(COMMON_VENDOR, commonVendor);
    }

    public void setUpdatePackage(String updatePackage) {
        addPair(UPDATE_PACKAGE, updatePackage);
    }

}
