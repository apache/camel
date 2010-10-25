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
 * An exception to veto starting {@link CamelContext}.
 *
 * @version $Revision$
 */
public class VetoCamelContextStartException extends Exception {
    private static final long serialVersionUID = 8046489554418284256L;
    private final CamelContext context;

    public VetoCamelContextStartException(String message, CamelContext context) {
        super(message);
        this.context = context;
    }

    public VetoCamelContextStartException(String message, Throwable cause, CamelContext context) {
        super(message, cause);
        this.context = context;
    }

    public CamelContext getContext() {
        return context;
    }
}
