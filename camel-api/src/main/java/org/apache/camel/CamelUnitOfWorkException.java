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

import java.util.List;

/**
 * A {@link org.apache.camel.spi.UnitOfWork} failed with a number of caused exceptions.
 * <p/>
 * This implementation will provide the first exception from the list in its cause, so its shown
 * in the stacktrace etc when logging this exception. But the remainder exceptions is only available
 * from the {@link #getCauses()} method.
 */
public class CamelUnitOfWorkException extends CamelExchangeException {
    private static final long serialVersionUID = 1L;
    private final List<Exception> causes;

    public CamelUnitOfWorkException(Exchange exchange, List<Exception> causes) {
        // just provide the first exception as cause, as it will be logged in the stacktraces
        super("Unit of work failed on exchange with " + causes.size()
                + " caused exceptions. First exception provided as cause to this exception.", exchange, causes.get(0));
        this.causes = causes;
    }

    public List<Exception> getCauses() {
        return causes;
    }
}
