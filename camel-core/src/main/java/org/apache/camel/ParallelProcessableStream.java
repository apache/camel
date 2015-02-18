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

import java.io.IOException;

/**
 * Tagging interface to indicate that a stream can be used in parallel
 * processing by offering a copy method.
 * <p/>
 * This is a useful feature for avoiding message re-readability issues which can
 * occur when the same message is processed by several threads. This interface
 * is mainly used by the {@link org.apache.camel.processor.MulticastProcessor}
 * and should be implemented by all implementers of
 * {@link org.apache.camel.StreamCache}
 * 
 * @version
 */
public interface ParallelProcessableStream {

    /**
     * Create a copy of the stream. If possible use the same cached data in the
     * copied instance.
     * <p>
     * This method is useful for parallel processing.
     * 
     * @throws java.io.IOException
     *             if the copy fails
     */
    ParallelProcessableStream copy() throws IOException;

}
