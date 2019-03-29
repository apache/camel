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
package org.apache.camel.component.quickfixj.examples.transform;

import java.util.Iterator;

import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.Field;
import quickfix.FieldMap;
import quickfix.FieldNotFound;
import quickfix.FieldType;
import quickfix.Group;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.Session;
import quickfix.SessionID;

public class QuickfixjMessageJsonTransformer {
   
    public String transform(Message message) throws FieldNotFound, ConfigError {
        SessionID sessionID = MessageUtils.getSessionID(message);
        Session session = Session.lookupSession(sessionID);
        DataDictionary dataDictionary = session.getDataDictionary();
        
        if (dataDictionary == null) {
            throw new IllegalStateException("No Data Dictionary. Exchange must reference an existing session");
        }
        
        return transform(message, dataDictionary);
    }

    public String transform(Message message, DataDictionary dataDictionary) {
        return transform(message, "", dataDictionary);
    }

    public String transform(Message message, String indent, DataDictionary dd) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("\"message\": ");
        if (message == null) {
            sb.append("null");
        } else {
            sb.append("{\n");
            String contentIndent = indent + "  ";
            
            transform("header", message.getHeader(), sb, contentIndent, dd);
            sb.append("\n");
            
            transform("body", message, sb, contentIndent, dd);
            sb.append("\n");

            transform("trailer", message.getTrailer(), sb, contentIndent, dd);
            sb.append("\n");
            
            sb.append(indent).append("}");
        }
        return sb.toString();
    }
    
    private void transform(String name, FieldMap fieldMap, StringBuilder sb, String indent, DataDictionary dd) {
        sb.append(indent).append("\"").append(name).append("\": {\n");
        int fieldCount = 0;
        Iterator<Field<?>> fieldIterator = fieldMap.iterator();
        while (fieldIterator.hasNext()) {
            if (fieldCount > 0) {
                sb.append(",\n");
            }
            Field<?> field = fieldIterator.next();
            sb.append(indent).append("  \"").append(dd.getFieldName(field.getField())).append("\": ");
            if (dd.hasFieldValue(field.getField())) {
                int tag = field.getField();
                sb.append("[ \"").append(field.getObject().toString()).append("\", \"").
                append(dd.getValueName(tag, field.getObject().toString())).
                append("\" ]");
            } else {
                FieldType fieldType = dd.getFieldType(field.getField());
                if (Number.class.isAssignableFrom(fieldType.getJavaType())) {
                    sb.append(field.getObject());
                } else {
                    sb.append("\"").append(field.getObject().toString()).append("\"");
                }
            }
            fieldCount++;
        }
        
        sb.append("\n");
        
        Iterator<Integer> groupKeys = fieldMap.groupKeyIterator();
        while (groupKeys.hasNext()) {
            int groupTag = groupKeys.next();
            for (Group group : fieldMap.getGroups(groupTag)) {
                String groupName = dd.getFieldName(groupTag);
                transform(groupName, group, sb, indent + "  ", dd);
            }
        }
        
        sb.append(indent).append("}").append("\n");
    }
}