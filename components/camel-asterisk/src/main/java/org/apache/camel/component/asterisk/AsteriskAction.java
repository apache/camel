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
package org.apache.camel.component.asterisk;

import java.util.function.Function;

import org.apache.camel.Exchange;
import org.asteriskjava.manager.action.ExtensionStateAction;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.action.QueueStatusAction;
import org.asteriskjava.manager.action.SipPeersAction;

public enum AsteriskAction implements Function<Exchange, ManagerAction> {
    QUEUE_STATUS {
        @Override
        public ManagerAction apply(Exchange exchange) {
            return new QueueStatusAction();
        }
    },
    SIP_PEERS {
        @Override
        public ManagerAction apply(Exchange exchange) {
            return new SipPeersAction();
        }
    },
    EXTENSION_STATE {
        @Override
        public ManagerAction apply(Exchange exchange) {
            return new ExtensionStateAction(
                exchange.getIn().getHeader(AsteriskConstants.EXTENSION, String.class),
                exchange.getIn().getHeader(AsteriskConstants.CONTEXT, String.class)
            );
        }
    }
}
