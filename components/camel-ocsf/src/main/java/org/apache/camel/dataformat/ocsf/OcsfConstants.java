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
package org.apache.camel.dataformat.ocsf;

/**
 * Constants for OCSF (Open Cybersecurity Schema Framework).
 */
public final class OcsfConstants {

    /**
     * Header to specify the OCSF event class for unmarshalling.
     */
    public static final String UNMARSHAL_TYPE = "CamelOcsfUnmarshalType";

    /**
     * Current OCSF schema version supported.
     */
    public static final String SCHEMA_VERSION = "1.7.0";

    // OCSF Event Categories
    public static final int CATEGORY_UNCATEGORIZED = 0;
    public static final int CATEGORY_SYSTEM_ACTIVITY = 1;
    public static final int CATEGORY_FINDINGS = 2;
    public static final int CATEGORY_IAM = 3;
    public static final int CATEGORY_NETWORK_ACTIVITY = 4;
    public static final int CATEGORY_DISCOVERY = 5;
    public static final int CATEGORY_APPLICATION_ACTIVITY = 6;
    public static final int CATEGORY_REMEDIATION = 7;

    // OCSF Event Class UIDs - System Activity Category
    public static final int CLASS_FILE_ACTIVITY = 1001;
    public static final int CLASS_KERNEL_EXTENSION_ACTIVITY = 1002;
    public static final int CLASS_KERNEL_ACTIVITY = 1003;
    public static final int CLASS_MEMORY_ACTIVITY = 1004;
    public static final int CLASS_MODULE_ACTIVITY = 1005;
    public static final int CLASS_SCHEDULED_JOB_ACTIVITY = 1006;
    public static final int CLASS_PROCESS_ACTIVITY = 1007;

    // OCSF Event Class UIDs - Findings Category
    public static final int CLASS_SECURITY_FINDING = 2001;
    public static final int CLASS_VULNERABILITY_FINDING = 2002;
    public static final int CLASS_COMPLIANCE_FINDING = 2003;
    public static final int CLASS_DETECTION_FINDING = 2004;
    public static final int CLASS_INCIDENT_FINDING = 2005;
    public static final int CLASS_DATA_SECURITY_FINDING = 2006;

    // OCSF Event Class UIDs - IAM Category
    public static final int CLASS_ACCOUNT_CHANGE = 3001;
    public static final int CLASS_AUTHENTICATION = 3002;
    public static final int CLASS_AUTHORIZE_SESSION = 3003;
    public static final int CLASS_ENTITY_MANAGEMENT = 3004;
    public static final int CLASS_USER_ACCESS_MANAGEMENT = 3005;
    public static final int CLASS_GROUP_MANAGEMENT = 3006;

    // OCSF Event Class UIDs - Network Activity Category
    public static final int CLASS_NETWORK_ACTIVITY = 4001;
    public static final int CLASS_HTTP_ACTIVITY = 4002;
    public static final int CLASS_DNS_ACTIVITY = 4003;
    public static final int CLASS_DHCP_ACTIVITY = 4004;
    public static final int CLASS_RDP_ACTIVITY = 4005;
    public static final int CLASS_SMB_ACTIVITY = 4006;
    public static final int CLASS_SSH_ACTIVITY = 4007;
    public static final int CLASS_FTP_ACTIVITY = 4008;
    public static final int CLASS_EMAIL_ACTIVITY = 4009;
    public static final int CLASS_NETWORK_FILE_ACTIVITY = 4010;
    public static final int CLASS_NTP_ACTIVITY = 4013;

    // OCSF Event Class UIDs - Application Activity Category
    public static final int CLASS_WEB_RESOURCES_ACTIVITY = 6001;
    public static final int CLASS_API_ACTIVITY = 6003;
    public static final int CLASS_DATASTORE_ACTIVITY = 6005;
    public static final int CLASS_SCAN_ACTIVITY = 6007;

    // OCSF Event Class UIDs - Remediation Category
    public static final int CLASS_REMEDIATION_ACTIVITY = 7001;

    // OCSF Severity IDs
    public static final int SEVERITY_UNKNOWN = 0;
    public static final int SEVERITY_INFORMATIONAL = 1;
    public static final int SEVERITY_LOW = 2;
    public static final int SEVERITY_MEDIUM = 3;
    public static final int SEVERITY_HIGH = 4;
    public static final int SEVERITY_CRITICAL = 5;
    public static final int SEVERITY_FATAL = 6;
    public static final int SEVERITY_OTHER = 99;

    // OCSF Activity IDs (common)
    public static final int ACTIVITY_UNKNOWN = 0;
    public static final int ACTIVITY_CREATE = 1;
    public static final int ACTIVITY_UPDATE = 2;
    public static final int ACTIVITY_CLOSE = 3;
    public static final int ACTIVITY_OTHER = 99;

    // OCSF Status IDs
    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILURE = 2;
    public static final int STATUS_OTHER = 99;

    private OcsfConstants() {
    }
}
