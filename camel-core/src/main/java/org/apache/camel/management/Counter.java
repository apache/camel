/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(
        description="Counter", 
        currencyTimeLimit=15)
public class Counter {

	protected AtomicLong numExchanges = new AtomicLong(0L);
	
	@ManagedOperation(description = "Reset counters")
	public void reset() {
		numExchanges.set(0L);
	}
	
	@ManagedAttribute(description = "Total number of exchanges")
	public long getNumExchanges() throws Exception {
		return numExchanges.get();
	}

	public long increment() {
		return numExchanges.incrementAndGet();
	}
}
