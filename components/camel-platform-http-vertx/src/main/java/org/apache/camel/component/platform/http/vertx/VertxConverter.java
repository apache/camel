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

package org.apache.camel.component.platform.http.vertx;

import io.vertx.core.net.impl.SocketAddressImpl;
import org.apache.camel.Converter;

@Converter(generateLoader = true)
public final class VertxConverter {

    /*
     * This converter provides a small performance boost when creating the HTTP headers, as it avoids a costlier conversion
     * path involving the core's ToStringTypeConverter (which is also slower due to multiple type misses). This one provides
     * a direct conversion from the converter cache.
     */
    @Converter
    public static String fromSocket(SocketAddressImpl socketAddress) {
        return socketAddress.toString();
    }
}
