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
 * Callback when processing an {@link Exchange} using {@link org.apache.camel.AsyncProcessor}
 * and the {@link Exchange} have received the data and is ready to be routed.
 *
 * @version $Revision$
 */
public interface AsyncCallback {

    /**
     * Callback when the {@link Exchange} is ready to be routed as data has been received.
     *
     * @param exchange the exchange
     */
    void onDataReceived(Exchange exchange);
}
