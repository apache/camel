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
package org.apache.camel.component.as2.api;

public interface MDNField {

    /**
     * Field Name for Reporting UA
     */
    public static final String REPORTING_UA = "Reporting-UA";

    /**
     * Field Name for MDN Gateway
     */
    public static final String MDN_GATEWAY = "MDN-Gateway";

    /**
     * Field Name for Final Recipient
     */
    public static final String FINAL_RECIPIENT = "Final-Recipient";

    /**
     * Field Name for Original Message IDX
     */
    public static final String ORIGINAL_MESSAGE_ID = "Original-Message-ID";

    /**
     * Field Name for Disposition
     */
    public static final String DISPOSITION = "Disposition";

    /**
     * Field Name for Failure
     */
    public static final String FAILURE = "Failure";

    /**
     * Field Name for Error
     */
    public static final String ERROR = "Error";

    /**
     * Field Name for Warning
     */
    public static final String WARNING = "Warning";

    /**
     * Field Name for Received Content MIC
     */
    public static final String RECEIVED_CONTENT_MIC = "Received-content-MIC";

}
