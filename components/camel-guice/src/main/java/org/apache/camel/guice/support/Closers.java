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
package org.apache.camel.guice.support;

/**
 * Some helper methods for working with the {@link Closer} interface
 * 
 * @version
 */
public final class Closers {
    private Closers() {
        //Helper class
    }

    /**
     * Closes the given object with the Closer if the object is not null
     * 
     * @param key
     *            the Key or String name of the object to be closed
     * @param objectToBeClosed
     *            the object that is going to be closed
     * @param closer
     *            the strategy used to close the object
     * @param errors
     *            the handler of exceptions if they occur
     */
    public static void close(Object key, Object objectToBeClosed,
            Closer closer, CloseErrors errors) {
        if (objectToBeClosed != null) {
            try {
                closer.close(objectToBeClosed);
            } catch (Exception e) {
                errors.closeError(key, objectToBeClosed, e);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }
}
