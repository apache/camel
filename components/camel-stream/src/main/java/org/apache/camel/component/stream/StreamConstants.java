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
package org.apache.camel.component.stream;

import org.apache.camel.spi.Metadata;

public final class StreamConstants {

    @Metadata(label = "consumer",
              description = "The index of the exchange in the stream, starting at 0 and counting each exchange"
                            + " that the consumer creates: a line, a group of lines, or the bytes of one read. With"
                            + " scanStream and retry, the count goes on after the stream is opened again.",
              javaType = "long")
    public static final String STREAM_INDEX = "CamelStreamIndex";
    @Metadata(label = "consumer",
              description = "Whether the exchange is the last one of the stream. The consumer reads one line ahead"
                            + " to know it, except for kind in, where it is true for each line. Always false with"
                            + " scanStream, as a scanned stream never ends.",
              javaType = "boolean")
    public static final String STREAM_COMPLETE = "CamelStreamComplete";

    private StreamConstants() {
    }
}
