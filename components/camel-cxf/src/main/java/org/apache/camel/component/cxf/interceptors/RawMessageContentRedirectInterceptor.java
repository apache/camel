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
package org.apache.camel.component.cxf.interceptors;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;

import org.apache.camel.StreamCache;
import org.apache.camel.util.IOHelper;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class RawMessageContentRedirectInterceptor extends AbstractPhaseInterceptor<Message> {

    public RawMessageContentRedirectInterceptor() {
        super(Phase.WRITE);
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        // check the fault from the message
        Throwable ex = message.getContent(Throwable.class);
        if (ex != null) {
            if (ex instanceof Fault) {
                throw (Fault)ex;
            } else {
                throw new Fault(ex);
            }
        }

        List<?> params = message.getContent(List.class);
        if (null != params) {
            InputStream is = (InputStream)params.get(0);
            OutputStream os = message.getContent(OutputStream.class);
            Writer writer = message.getContent(Writer.class);
            if (os == null && writer == null) {
                //InOny
                return;
            }
            try {
                if (os == null && writer != null) {
                    IOUtils.copyAndCloseInput(new InputStreamReader(is), writer);
                } else {
                    if (is instanceof StreamCache) {
                        ((StreamCache)is).writeTo(os);
                    } else {
                        IOUtils.copy(is, os);
                    }
                }
            } catch (Exception e) {
                throw new Fault(e);
            } finally {
                IOHelper.close(is, "input stream", null);
                // Should not close the output stream as the interceptor chain will close it
            }
        }
    }
}
