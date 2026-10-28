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
package org.apache.camel.spi;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.jspecify.annotations.Nullable;

/**
 * Computes the checksum of a route as written, so tools can tell which routes changed between two runs or after a
 * reload: the same route gives the same checksum in every run.
 * <p/>
 * The checksum is a hash of the whole route: its steps, their options and expressions, with property placeholders as
 * written. It leaves out what does not change the route: the ids Camel generates, and where the route is in its source
 * (moving the route in its file, or adding a route above it, keeps its checksum).
 * <p/>
 * The implementation is provided by {@code camel-xml-io}, which writes every option of the model, and is discovered via
 * the service key {@link #FACTORY}. It is a single implementation, so a route gets the same checksum whichever modules
 * an application has.
 *
 * @since 4.24
 */
public interface ModelChecksumGenerator {

    /**
     * Service factory key.
     */
    String FACTORY = "model-checksum-generator";

    /**
     * Computes the checksum of the definition as written.
     *
     * @param  context    the CamelContext
     * @param  definition the definition, such as a route
     * @return            the checksum, a short hex digest, or <tt>null</tt> when it cannot be computed
     */
    @Nullable
    String generateChecksum(CamelContext context, NamedNode definition);
}
