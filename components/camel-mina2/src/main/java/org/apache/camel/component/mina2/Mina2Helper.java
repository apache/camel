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
package org.apache.camel.component.mina2;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class used internally by camel-mina using Apache MINA.
 */
public final class Mina2Helper {

    private static final Logger LOG = LoggerFactory.getLogger(Mina2Helper.class);

    private Mina2Helper() {
        //Utility Class
    }

    /**
     * Writes the given body to MINA session. Will wait until the body has been written.
     *
     * @param session  the MINA session
     * @param body     the body to write (send)
     * @param exchange the exchange
     * @throws CamelExchangeException is thrown if the body could not be written for some reasons
     *                                (eg remote connection is closed etc.)
     */
    public static void writeBody(IoSession session, Object body, Exchange exchange) throws CamelExchangeException {
        LOG.trace("write exchange [{}] with body [{}]", exchange, body);
        // the write operation is asynchronous
        session.write(body);
    }
}
