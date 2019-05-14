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
package org.apache.camel;

/**
 * Base class for all Camel unchecked exceptions.
 */
public class RuntimeCamelException extends RuntimeException {
    private static final long serialVersionUID = 8046489554418284257L;

    public RuntimeCamelException() {
    }

    public RuntimeCamelException(String message) {
        super(message);
    }

    public RuntimeCamelException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeCamelException(Throwable cause) {
        super(cause);
    }

    /**
     * Wraps the caused exception in a {@link RuntimeCamelException} if its not
     * already such an exception.
     *
     * @param e the caused exception
     * @return the wrapper exception
     */
    public static RuntimeCamelException wrapRuntimeCamelException(Throwable e) {
        if (e instanceof RuntimeCamelException) {
            // don't double wrap
            return (RuntimeCamelException) e;
        } else {
            return new RuntimeCamelException(e);
        }
    }

    /**
     * Wraps the caused exception in a {@link RuntimeCamelException} if its not
     * already a runtime exception.
     *
     * @param e the caused exception
     * @return the wrapper exception
     */
    public static RuntimeException wrapRuntimeException(Throwable e) {
        if (e instanceof RuntimeException) {
            // don't double wrap
            return (RuntimeException) e;
        } else {
            return new RuntimeCamelException(e);
        }
    }
}
