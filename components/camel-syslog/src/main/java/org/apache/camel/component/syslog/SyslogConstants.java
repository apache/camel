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
package org.apache.camel.component.syslog;

public final class SyslogConstants {

    /**
     * The socket address of local machine that received the message.
     */
    public static final String SYSLOG_LOCAL_ADDRESS = "CamelSyslogLocalAddress";

    /**
     * The socket address of the remote machine that send the message.
     */
    public static final String SYSLOG_REMOTE_ADDRESS = "CamelSyslogRemoteAddress";

    /**
     * The Sylog message Facility
     */
    public static final String SYSLOG_FACILITY = "CamelSyslogFacility";

    /**
     * The Syslog severity
     */
    public static final String SYSLOG_SEVERITY = "CamelSyslogSeverity";

    /**
     * The hostname in the syslog message
     */
    public static final String SYSLOG_HOSTNAME = "CamelSyslogHostname";

    /**
     * The syslog timestamp
     */
    public static final String SYSLOG_TIMESTAMP = "CamelSyslogTimestamp";

    private SyslogConstants() {
        // Utility class
    }
}
