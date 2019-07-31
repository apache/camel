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
package org.apache.camel.component.quickfixj;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;
import quickfix.Field;
import quickfix.FieldMap;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;

public class MessagePredicate {
    private final List<Field<String>> headerCriteria = new ArrayList<>();
    private final List<Field<String>> bodyCriteria = new ArrayList<>();
    
    public MessagePredicate(SessionID requestingSessionID, String msgType) {
        // TODO may need to optionally include subID and locationID
        addHeaderFieldIfPresent(SenderCompID.FIELD, requestingSessionID.getSenderCompID());
        addHeaderFieldIfPresent(TargetCompID.FIELD, requestingSessionID.getTargetCompID());
        withMessageType(msgType);
    }
    
    private void addHeaderFieldIfPresent(int tag, String value) {
        if (!ObjectHelper.isEmpty(value)) {
            withHeaderField(tag, value);
        }
    }

    public boolean evaluate(Message message) {
        return evaluate(message, bodyCriteria) && evaluate(message.getHeader(), headerCriteria);
    }
    
    private boolean evaluate(FieldMap fieldMap, List<Field<String>> criteria) {
        for (Field<String> c : criteria) {
            String value = null;
            try {
                if (fieldMap.isSetField(c.getField())) {
                    value = fieldMap.getString(c.getField());
                }
            } catch (FieldNotFound e) {
                RuntimeCamelException.wrapRuntimeCamelException(e);
            }
            if (!c.getObject().equals(value)) {
                return false;
            }
        }
        return true;
    }

    public MessagePredicate withField(int tag, String value) {
        bodyCriteria.add(new Field<>(tag, value));
        return this;
    }

    public MessagePredicate withHeaderField(int tag, String value) {
        headerCriteria.add(new Field<>(tag, value));
        return this;
    }

    private MessagePredicate withMessageType(String msgType) {
        headerCriteria.add(new Field<>(MsgType.FIELD, msgType));
        return this;
    }
}
