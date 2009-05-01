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
 * The callback interface for an {@link AsyncProcessor} so that it can
 * notify you when an {@link Exchange} has completed.
 *
 * @deprecated a new async API is planned for Camel 2.0
 */
public interface AsyncCallback {
    
    /**
     * This method is invoked once the Exchange is completed.  If an error 
     * occurred while processing the exchange, the exception field of the 
     * {@link Exchange} being processed will hold the error.
     * <p/>
     * This callback reports back twice:
     * - first time when the caller thread is done, that is the synchronously done.
     * - second time when the asynchronously thread is done and thus the {@link Exchange} is really complete. 
     *  
     * @param doneSynchronously set to <tt>true</tt> if the processing of the exchange was completed in the
     * synchronously thread. Is set to <tt>false</tt> when the asynchronously thread is complete.
     */
    void done(boolean doneSynchronously);
    
}
