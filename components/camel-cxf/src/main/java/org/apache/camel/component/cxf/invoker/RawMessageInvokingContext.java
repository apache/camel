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
package org.apache.camel.component.cxf.invoker;

import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;

public class RawMessageInvokingContext extends AbstractInvokingContext {
    private static final Logger LOG = LogUtils.getL7dLogger(RawMessageInvokingContext.class);

    public RawMessageInvokingContext() {

    }

    public void setRequestOutMessageContent(Message message, Map<Class, Object> contents) {
        Set entries = contents.keySet();
        Iterator iter = entries.iterator();
        while (iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof Class) {
                message.setContent((Class<?>)obj, contents.get((Class<?>)obj));
            }
        }
    }

    public Object getResponseObject(Exchange exchange, Map<String, Object> responseContext) {

        return getResponseObject(exchange.getInMessage(), responseContext, InputStream.class);
    }

    public void setResponseContent(Message outMessage, Object resultPayload) {
        LOG.info("Set content: " + resultPayload);
        outMessage.setContent(InputStream.class, resultPayload);
        //loggerTheMessage(outMessage, "Out Message");
    }

    public Map<Class, Object> getRequestContent(Message inMessage) {
        //loggerTheMessage(inMessage, "In Message");

        IdentityHashMap<Class, Object> contents = new IdentityHashMap<Class, Object>();

        Set set = inMessage.getContentFormats();
        Iterator iter = set.iterator();
        while (iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof Class) {
                contents.put((Class<?>)obj, inMessage.getContent((Class<?>)obj));
            }
        }

        return contents;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

}
