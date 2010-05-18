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
 * An <b>asynchronous</b> processor which can process an {@link Exchange} in an asynchronous fashion
 * and signal completion by invoking the {@link AsyncCallback}.
 * <p/>
 * For example {@link Producer} can implement this interface to support real asynchronous non blocking
 * when using the {@link org.apache.camel.processor.SendAsyncProcessor}.
 *
 * @version $Revision$
 * @deprecated will be replaced with a new async routing engine in Camel 2.4. So expect this interface to change
 */
@Deprecated
public interface AsyncProcessor extends Processor {

    /**
     * Processes the message exchange
     *
     * @param exchange the message exchange
     * @param callback the callback to invoke when data has been received and the {@link Exchange}
     * is ready to be continued routed.
     * @throws Exception if an internal processing error has occurred.
     */
    void process(Exchange exchange, AsyncCallback callback) throws Exception;
}
