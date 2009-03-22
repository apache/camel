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
package org.apache.camel;

/**
 * This converter is capable of converting from one message 
 * @version $Revision$
 * @since 2.0
 */
public interface MessageConverter {
    
    /**
     * Converts the IN message of a given Camel Exchange to the new type
     * 
     * @param <T> the new type
     * @param type the new class type
     * @param exchange the exchange that contains the source message
     * @return
     */
    <T> T convertInMessageTo(Class<T> type, Exchange exchange);
    
    /**
     * Converts the OUT message of a given Camel Exchange to the new type
     * 
     * @param <T>
     * @param type
     * @param exchange
     * @return
     */
    <T> T convertOutMessageTo(Class<T> type, Exchange exchange);

}
