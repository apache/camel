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
package org.apache.camel.component.dataweave;

/**
 * Thrown when a DataWeave script cannot be converted to DataSonnet, either because the parser encountered a syntax
 * error or because a construct is not supported by the converter.
 * <p>
 * When thrown by the parser, the message includes the DataWeave source location (line:column) and the
 * unexpected token. When thrown for unsupported constructs detected after conversion, the message
 * describes the failure but does not include a source location.
 */
public class DataWeaveConversionException extends RuntimeException {

    public DataWeaveConversionException(String message) {
        super(message);
    }

    public DataWeaveConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
