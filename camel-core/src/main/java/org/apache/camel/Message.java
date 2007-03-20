/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel;

import java.util.Map;

/**
 * Represents an inbound or outbound message as part of an {@link Exchange}
 *
 * @version $Revision$
 */
public interface Message {

    /**
     * Access the headers on the message
     */
    public Headers getHeaders();

    /**
     * Returns the body of the message as a POJO
     *
     * @returns the body of the message
     */
    public Object getBody();

    /**
     * Returns the body as the specified type
     *
     * @param type the type that the body i
     * @return the body of the message as the specified type
     */
    public <T> T getBody(Class<T> type);

    /**
     * Sets the body of the message
     */
    public void setBody(Object body);

    /**
     * Sets the body of the message as a specific type
     */
    public <T> void setBody(Object body, Class<T> type);

    /**
     * Creates a copy of this message so that it can be used and possibly modified further in another exchange
     * 
     * @return a new message instance copied from this message
     */
    public Message copy();
}
