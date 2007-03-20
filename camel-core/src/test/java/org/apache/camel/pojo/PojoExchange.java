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
package org.apache.camel.pojo;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.impl.ExchangeSupport;


/**
 * @version $Revision: 519901 $
 */
public class PojoExchange extends ExchangeSupport<PojoInvocation, Object, Throwable> {
	
	Map<String, Object> headers;
	
	public Object getHeader(String name) {
		if( headers == null )
			return null;
		return headers.get(name);
	}

	public Map<String, Object> getHeaders() {
		if( headers == null )
			headers = new HashMap<String, Object>();
		return headers;
	}
	
	public void setHeader(String name, Object value) {
		getHeaders().put(name, value);
	}

}
