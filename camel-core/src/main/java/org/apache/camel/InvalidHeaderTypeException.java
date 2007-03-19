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
package org.apache.camel;

/**
 * @version $Revision$
 */
public class InvalidHeaderTypeException extends RuntimeCamelException {

	private static final long serialVersionUID = -8417806626073055262L;
	private Object headerValue;

    public InvalidHeaderTypeException(Throwable cause, Object headerValue) {
        super(cause.getMessage() + " headerValue is: " + headerValue + " of type: "
                + typeName(headerValue), cause);
        this.headerValue = headerValue;
    }

    public InvalidHeaderTypeException(String message, Object headerValue) {
        super(message);
        this.headerValue = headerValue;
    }


    /**
     * Returns the actual header value
     */
    public Object getHeaderValue() {
        return headerValue;
    }

    protected static String typeName(Object headerValue) {
        return (headerValue != null) ? headerValue.getClass().getName() : "null";
    }
}
