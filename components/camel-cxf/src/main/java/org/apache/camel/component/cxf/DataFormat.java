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
package org.apache.camel.component.cxf;

/**
 * The data format the user expects to see at the Camel CXF components.  It can be
 * configured as a property (DataFormat) in the Camel CXF endpoint.
 */
public enum DataFormat {

    /**
     * PAYLOAD is the message payload of the message after message configured in
     * the CXF endpoint is applied.  Streaming and non-streaming are both
     * supported.
     */
    PAYLOAD,


    /**
     * <p>
     * RAW is the raw message that is received from the transport layer.
     * Streaming and non-streaming are both supported.
     * <p>
     * <p>
     * Only the interceptors of these phases are <b>preserved</b>:
     * </p>
     * <p>
     * In phases: {Phase.RECEIVE , Phase.INVOKE, Phase.POST_INVOKE}
     * </p>
     * <p>
     * Out phases: {Phase.PREPARE_SEND, Phase.WRITE, Phase.SEND, Phase.PREPARE_SEND_ENDING}
     * </p>
     * 
     */
    RAW,
    
    /**
     * MESSAGE is the raw message that is received from the transport layer.
     * Streaming and non-streaming are both supported.
     * @deprecated - equivalent to RAW mode for Camel 2.x
     */
    @Deprecated
    MESSAGE {
        public DataFormat dealias() {
            return RAW;
        }
    },

    /**
     * CXF_MESSAGE is the message that is received from the transport layer
     * and then processed through the full set of CXF interceptors.  This 
     * provides the same functionality as the CXF MESSAGE mode providers.
     */
    CXF_MESSAGE,    
    
    /**
     * POJOs (Plain old Java objects) are the Java parameters to the method
     * it is invoking on the target server.  The "serviceClass" property
     * must be included in the endpoint.  Streaming is not available for this
     * data format.
     */
    POJO;
    
    public DataFormat dealias() {
        return this;
    }
    
}
