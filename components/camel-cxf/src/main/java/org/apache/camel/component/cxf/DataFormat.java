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
package org.apache.camel.component.cxf;

/**
 * The data format the user expoects to see at the camel cxf components.  It can be
 * configured as a property (DataFormat) in the Camel cxf endpoint.
 */
public enum DataFormat {

    /**
     * PAYLOAD is the message payload of the message after message configured in
     * the CXF endpoint is applied.  Streaming and non-streaming are both
     * supported.
     */
    PAYLOAD,

    /**
     * MESSAGE is the raw message that is received from the transport layer.
     * Streaming and non-streaming are both supported.
     */
    MESSAGE,

    /**
     * POJOs (Plain old Java objects) are the Java parameters to the method
     * it is invoking on the target server.  The "serviceClass" property
     * must be included in the endpoint.  Streaming is not available for this
     * data format.
     */
    POJO
}
