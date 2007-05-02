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

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;

/**
 * Marshallers that marshall to XML should extend this base class.
 *
 * @version $Revision: 520124 $
 */
public abstract class XmlMarshaller implements Marshaller{
	
	/**
     * Marshals the object to the given Stream.
     */
	public void marshal(Object object, OutputStream result) throws IOException {
		marshal(object, new StreamResult(result));
	}

	abstract public void marshal(Object object, Result result);
}
