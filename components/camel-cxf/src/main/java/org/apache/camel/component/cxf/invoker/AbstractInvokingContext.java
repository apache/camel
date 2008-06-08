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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;

/**
 * A RoutingContext encapulates specific knowledge about how to route messages of
 * a particular data format.
 *
 */
public abstract class AbstractInvokingContext implements InvokingContext {

    protected abstract Logger getLogger();

    protected <T> T getResponseObject(Message inMessage, Map<String, Object> responseContext,
            Class <T> clazz) {
        T retval = null;
        if (inMessage != null) {
            if (null != responseContext) {
                responseContext.putAll(inMessage);
                getLogger().info("set responseContext to be" + responseContext);
            }
            retval = inMessage.getContent(clazz);
        }
        return retval;
    }

    protected void loggerTheMessage(Message message, String messageTile) {
        StringBuffer buffer = new StringBuffer(messageTile + "\n"
                                               + "--------------------------------------");
        InputStream is = message.getContent(InputStream.class);
        if (is != null) {
            CachedOutputStream bos = new CachedOutputStream();
            try {
                IOUtils.copy(is, bos);

                is.close();
                bos.close();

                buffer.append("\nMessage:\n");
                buffer.append(bos.getOut().toString());

                message.setContent(InputStream.class, bos.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        buffer.append("\n--------------------------------------");
        getLogger().info(buffer.toString());
    }

}
