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
package org.apache.camel.component.cxf;

import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.cxf.message.MessageContentsList;

/**
 * A simple CxfMessage object.
 */
public class CxfMessage extends DefaultMessage {

    /**
     * If the body is a {@link MessageContentsList}, this getBody method
     * applies converters to the first element of the MessageContentsList
     * and returns the value.    
     * */
    @Override
    public <T> T getBody(Class<T> type) {
        if (!(MessageContentsList.class.isAssignableFrom(type)) 
                && getBody() instanceof MessageContentsList) {
            // if the body is the MessageContentsList then try to convert its payload
            // to make it easier for end-users to use camel-cxf
            MessageContentsList list = (MessageContentsList)getBody();
            for (int i = 0; i < list.size(); i++) {
                Object value = list.get(i);
                try {
                    T answer = getBody(type, value);
                    if (answer != null) {
                        return answer;
                    }
                } catch (NoTypeConversionAvailableException e) {
                    // ignore
                }
            }
        }

        // default to super
        return super.getBody(type);
    }

}
