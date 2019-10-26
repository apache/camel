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
package org.apache.camel.component.as2.api;

import org.apache.http.protocol.HttpCoreContext;

/**
 * Constants for AS2 component.
 */
public interface AS2Constants {

    /**
     * The Value of User Agent Header used by AS2 Camel Component.
     */
    String HTTP_USER_AGENT = "Camel AS2 Component";

    /**
     * The Value of Origin Server Header used by AS2 Camel Component.
     */
    String HTTP_ORIGIN_SERVER = "Camel AS2 Component";

    /**
     * Fully Qualified Domain Name used by AS2 Camel Component in Message ID Header.
     */
    String HTTP_MESSAGE_ID_FQDN = "camel.apache.org";

    /**
     * The Value of User Agent Header used by AS2 Camel Component.
     */
    String MIME_VERSION = "1.0";

    //
    // HTTP Context Attribute Names
    //

    /**
     * HTTP Context Attribute Name for HTTP Client Connection object stored in context.
     */
    String HTTP_CLIENT_CONNECTION = HttpCoreContext.HTTP_CONNECTION;

    /**
     * HTTP Context Attribute Name for HTTP Client Processor object stored in context.
     */
    String HTTP_CLIENT_PROCESSOR = "http.processor";

    /**
     * HTTP Context Attribute Name for HTTP Client Fully Qualified Domain Name (FQDN) stored in context.
     */
    String HTTP_CLIENT_FQDN = "client.fqdn";

    /**
     * HTTP Context Attribute Name for HTTP Server Connection object stored in context.
     */
    String HTTP_SERVER_CONNECTION = "http.server.connection";

    /**
     * HTTP Context Attribute Name for HTTP Server Processor object stored in context.
     */
    String HTTP_SERVER_PROCESSOR = "http.server.processor";

    /**
     * HTTP Context Attribute Name for HTTP Server Service object stored in context.
     */
    String HTTP_SERVER_SERVICE = "http.server.service";


    //
    // AS2 MIME Content Types
    //

    /**
     * Application EDIFACT content type
     */
    String APPLICATION_EDIFACT_MIME_TYPE  = "Application/EDIFACT";

}
