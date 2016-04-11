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

package org.apache.camel.component.cxf.interceptors;

import java.io.IOException;
import java.io.InputStream;

import org.apache.cxf.interceptor.OutgoingChainInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;


public class OneWayOutgoingChainInterceptor extends AbstractPhaseInterceptor<Message> {
    
    public OneWayOutgoingChainInterceptor() {
        super(Phase.POST_INVOKE);
        this.addBefore(OutgoingChainInterceptor.class.getName());
    }

    public void handleMessage(Message message) {
        closeInput(message);
        return;
    }
    
    private void closeInput(Message message) {
        InputStream is = message.getContent(InputStream.class);
        if (is != null) {
            try {
                is.close();
                message.removeContent(InputStream.class);
            } catch (IOException ioex) {
                //ignore
            }
        }
    }
}
