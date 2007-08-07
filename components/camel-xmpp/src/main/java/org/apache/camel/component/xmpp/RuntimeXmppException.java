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
package org.apache.camel.component.xmpp;

import org.jivesoftware.smack.XMPPException;

/**
 * A runtime exception thrown if sending or receiving from XMPP fails
 *
 * @version $Revision:520964 $
 */
public class RuntimeXmppException extends RuntimeException {
    private static final long serialVersionUID = -2141493732308871761L;

    public RuntimeXmppException(XMPPException cause) {
        super(cause);
    }
    public RuntimeXmppException(String message, XMPPException cause) {
        super(message, cause);
    }

}
