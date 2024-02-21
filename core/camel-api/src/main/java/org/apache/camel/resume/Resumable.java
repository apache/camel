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

package org.apache.camel.resume;

/**
 * This provides an interface for resumable objects. Such objects allow its users to address them at a specific offset.
 * For example, when reading large files, it may be possible to inform the last offset that was read, thus allowing
 * users of this interface to skip to that offset. This can potentially improve resumable operations by allowing
 * reprocessing of data.
 */
public interface Resumable {

    /**
     * Gets the offset key (i.e.: the addressable part of the resumable object)
     *
     * @return An OffsetKey instance with the addressable part of the object. May return null or an EmptyOffset
     *         depending on the type of the resumable
     */
    OffsetKey<?> getOffsetKey();

    /**
     * Gets the last offset
     *
     * @return the last offset value according to the interface and type implemented
     */
    Offset<?> getLastOffset();
}
