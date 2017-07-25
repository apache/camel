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

import java.io.IOException;

import org.apache.http.protocol.HttpRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AS2 Server Manager 
 * 
 * <p>Receives EDI Messages over HTTP  
 *
 */
public class AS2ServerManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(AS2ServerManager.class);
    
    private AS2ServerConnection as2ServerConnection;
    
    public AS2ServerManager(AS2ServerConnection as2ServerConnection) {
        this.as2ServerConnection = as2ServerConnection;
    }
    
    public void listen(String requestUriPattern, HttpRequestHandler handler) {
        try {
            as2ServerConnection.listen(requestUriPattern, handler);
        } catch (IOException e) {
            LOG.error("Failed to listen for '" + requestUriPattern + "' requests: " + e.getMessage(), e);
            throw new RuntimeException("Failed to listen for '" + requestUriPattern + "' requests: " + e.getMessage(), e);
        }
                
    }
    
    public void stopListening(String requestUri) {
        as2ServerConnection.stopListening(requestUri);
    }
    
}
