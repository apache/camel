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
package org.apache.camel.component.mina;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;
import org.apache.mina.common.IoSession;

/**
 * A {@link Exchange} for Apache MINA.
 * 
 * @version $Revision$
 */
public class MinaExchange extends DefaultExchange {

    private IoSession session;

    public MinaExchange(CamelContext camelContext, ExchangePattern pattern, IoSession session) {
        super(camelContext, pattern);
        this.session = session;
    }
    
    public MinaExchange(DefaultExchange parent, IoSession session) {
        super(parent);
        this.session = session;
    }


    /**
     * The associated Mina session, is <b>only</b> available for {@link MinaConsumer}.
     * 
     * @return the Mina session.
     */
    public IoSession getSession() {
        return session;
    }
    
    @Override
    public Exchange newInstance() {
        return new MinaExchange(this, getSession());
    }


}
