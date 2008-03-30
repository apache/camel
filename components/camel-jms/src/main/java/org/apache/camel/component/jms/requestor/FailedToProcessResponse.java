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
package org.apache.camel.component.jms.requestor;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.camel.RuntimeCamelException;

/**
 * An exception thrown if a response message from an InOut could not be processed
 *
 * @version $Revision$
 */
public class FailedToProcessResponse extends RuntimeCamelException {
    private final Message response;

    public FailedToProcessResponse(Message response, JMSException e) {
        super("Failed to process response: " + e + ". Message: " + response, e);
        this.response = response;
    }

    /**
     * The response message which caused the exception
     */
    public Message getResponse() {
        return response;
    }
}
