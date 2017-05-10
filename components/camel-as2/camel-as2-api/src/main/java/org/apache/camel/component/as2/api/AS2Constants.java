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

import org.apache.http.protocol.HttpCoreContext;

/**
 * Constants for AS2 component.
 */
public interface AS2Constants {
    
    //
    // HTTP Context Attribute Names
    //

    /**
     * HTTP Context Attribute Name for HTTP Connection object stored in context.
     */
    public static final String HTTP_CONNECTION = HttpCoreContext.HTTP_CONNECTION;
    
    /**
     * HTTP Context Attribute Name for HTTP Processor object stored in context.
     */
    public static final String HTTP_PROCESSOR = "http.processor";
    
    /**
     * HTTP Context Attribute Name for Client Fully Qualified Domain Name (FQDN) stored in context.
     */
    public static final String CLIENT_FQDN = "client.fqdn";
    
    //
    // AS2 Header Names
    //
    
    /**
     * Message Header Name for AS2 Version
     */
    public static final String AS2_VERSION_HEADER = "AS2-Version";
    
    /**
     * Message Header Name for Content Type
     */
    public static final String CONTENT_TYPE_HEADER = "Content-Type";
    
    /**
     * Message Header Name for AS2 From
     */
    public static final String AS2_FROM_HEADER = "AS2-From";
    
    /**
     * Message Header Name for AS2 To
     */
    public static final String AS2_TO_HEADER = "AS2-To";
    
    /**
     * Message Header Name for Subject
     */
    public static final String SUBJECT_HEADER = "Subject";
    
    /**
     * Message Header Name for Message ID
     */
    public static final String MESSAGE_ID_HEADER = "Message-Id";
    
    
    //
    // AS2 MIME Content Types
    //
    
    /**
     * Application EDIFACT content type
     */
    public static final String APPLICATION_EDIFACT_MIME_TYPE  = "Application/EDIFACT";
    
}
