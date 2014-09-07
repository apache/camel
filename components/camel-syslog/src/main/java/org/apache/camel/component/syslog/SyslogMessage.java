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

import java.util.Calendar;

public class SyslogMessage {

    private SyslogFacility facility;
    private SyslogSeverity severity;
    private String remoteAddress;
    private String localAddress;
    private String hostname;
    private String logMessage;

    private Calendar timestamp;

    public String getLogMessage() {
        return logMessage;
    }

    public void setLogMessage(String logMessage) {
        this.logMessage = logMessage;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public SyslogFacility getFacility() {
        return facility;
    }

    public void setFacility(SyslogFacility facility) {
        this.facility = facility;
    }

    public Calendar getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Calendar timestamp) {
        this.timestamp = timestamp;
    }

    public SyslogSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(SyslogSeverity severity) {
        this.severity = severity;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public String toString() {
        return "SyslogMessage{" + "content='" + logMessage + '\'' + ", facility=" + facility + ", severity=" + severity + ", remoteAddress='" + remoteAddress + '\''
                + ", localAddress='" + localAddress + '\'' + ", hostname='" + hostname + '\'' + ", messageTime=" + timestamp + '}';
    }
}
