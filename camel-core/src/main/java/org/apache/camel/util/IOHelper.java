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
package org.apache.camel.util;

import java.io.IOException;

/**
 * IO helper class.
 *
 * @version $Revision$
 */
public final class IOHelper {

    private IOHelper() {
        //Utility Class
    }

    /**
     * A factory method which creates an {@link IOException} from the given
     * exception and message
     */
    public static IOException createIOException(Throwable cause) {
        return createIOException(cause.getMessage(), cause);
    }

    /**
     * A factory method which creates an {@link IOException} from the given
     * exception and message
     */
    public static IOException createIOException(String message, Throwable cause) {
        IOException answer = new IOException(message);
        answer.initCause(cause);
        return answer;
    }
}
