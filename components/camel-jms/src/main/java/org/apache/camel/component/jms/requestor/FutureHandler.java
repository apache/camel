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

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.jms.JMSException;
import javax.jms.Message;

/**
 * A {@link FutureTask} which implements {@link ReplyHandler}
 * so that it can be used as a handler for a correlation ID
 *
 * @version $Revision$
 */
public class FutureHandler extends FutureTask<Message> implements ReplyHandler {
    
    private static final Callable<Message> EMPTY_CALLABLE = new Callable<Message>() {
        public Message call() throws Exception {
            return null;
        }
    };

    public FutureHandler() {
        super(EMPTY_CALLABLE);
    }

    public synchronized void set(Message result) {
        super.set(result);
    }

    public boolean handle(Message message) throws JMSException {
        set(message);
        return true;
    }
}
