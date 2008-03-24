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

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class RawMessageContentRedirectInterceptor extends AbstractPhaseInterceptor<Message> {
    public RawMessageContentRedirectInterceptor() {
        super(Phase.WRITE);
    }

    public void handleMessage(Message message) throws Fault {
        // check the fault from the message
        Exception ex = message.getContent(Exception.class);
        if (ex != null) {
            if (ex instanceof Fault) {
                throw (Fault)ex;
            } else {
                throw new Fault(ex);
            }
        }

        InputStream is = message.getContent(InputStream.class);
        OutputStream os = message.getContent(OutputStream.class);

        try {
            IOUtils.copy(is, os);
            is.close();
            os.flush();
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
}
