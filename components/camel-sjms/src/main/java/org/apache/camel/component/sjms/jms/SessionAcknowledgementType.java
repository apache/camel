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
package org.apache.camel.component.sjms.jms;

import javax.jms.Session;

/**
 * Session acknowledge enum keys
 */
public enum SessionAcknowledgementType {
    AUTO_ACKNOWLEDGE(Session.AUTO_ACKNOWLEDGE),
    CLIENT_ACKNOWLEDGE(Session.CLIENT_ACKNOWLEDGE),
    DUPS_OK_ACKNOWLEDGE(Session.DUPS_OK_ACKNOWLEDGE),
    SESSION_TRANSACTED(Session.SESSION_TRANSACTED);

    private int intValue = -1;

    SessionAcknowledgementType(int intValue) {
        this.intValue = intValue;
    }

    public int intValue() {
        return intValue;
    }
}
