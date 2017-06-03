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

public class Rfc5424SyslogMessage extends SyslogMessage {
    private String appName = "-";
    private String procId = "-";
    private String msgId = "-";
    private String structuredData = "-";

    /**
     * @return the appName
     */
    public String getAppName() {
        return appName;
    }

    /**
     * @param appName the appName to set
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /**
     * @return the procId
     */
    public String getProcId() {
        return procId;
    }

    /**
     * @param procId the procId to set
     */
    public void setProcId(String procId) {
        this.procId = procId;
    }

    /**
     * @return the msgId
     */
    public String getMsgId() {
        return msgId;
    }

    /**
     * @param msgId the msgId to set
     */
    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    /**
     * @return the structuredData
     */
    public String getStructuredData() {
        return structuredData;
    }

    /**
     * @param structuredData the structuredData to set
     */
    public void setStructuredData(String structuredData) {
        this.structuredData = structuredData;
    }

}
