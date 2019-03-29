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
package org.apache.camel.component.xmpp;

/**
 * Constants used in Camel XMPP module
 */
public interface XmppConstants {
    String MESSAGE_TYPE = "CamelXmppMessageType";
    String SUBJECT = "CamelXmppSubject";
    String THREAD_ID = "CamelXmppThreadID";
    String FROM = "CamelXmppFrom";

    /**
     * @deprecated use {@link #STANZA_ID} instead.
     */
    @Deprecated
    String PACKET_ID = "CamelXmppPacketID";

    String STANZA_ID = "CamelXmppStanzaID";
    String TO = "CamelXmppTo";
    String DOC_HEADER = "CamelXmppDoc";
}
