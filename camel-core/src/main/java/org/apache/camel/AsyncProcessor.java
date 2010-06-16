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
 *
 * @version $Revision$
 */
public interface AsyncProcessor extends Processor {

    /**
     * Processes the message exchange
     * <p/>
     * If there was a failure processing then the caused {@link Exception} would be set on the {@link Exchange}.
     *
     * @param exchange the message exchange
     * @param callback the callback to invoke when data has been received and the {@link Exchange}
     *                 is ready to be continued routed.
     * @return <tt>true</tt> to continue execute synchronously, <tt>false</tt> to continue being executed asynchronously
     */
    boolean process(Exchange exchange, AsyncCallback callback);
}
