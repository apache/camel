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
package org.apache.camel.component.hibernate;

import org.apache.camel.spi.Metadata;

public final class HibernateConstants {

    @Metadata(description = "The named parameters, as a Map, that the producer binds to the selection or mutation query. For"
                            + " a natural-id lookup the entries are added to the naturalId values and replace those of the"
                            + " same name. The values are not evaluated as expressions.",
              javaType = "Map<String, Object>",
              label = "producer")
    public static final String HIBERNATE_PARAMETERS = "CamelHibernateParameters";

    static final String HIBERNATE_SESSION_CONTEXT = "CamelHibernateSessionContext";

    private HibernateConstants() {
    }
}
