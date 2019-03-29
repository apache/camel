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
package org.apache.camel.component.springldap;

import java.util.function.BiFunction;

import org.springframework.ldap.core.LdapOperations;

/**
 * The list of supported LDAP operations. Currently supported operations are
 * search, bind, and unbind, authenticate and modify_attributes. The
 * function_driven operation expects a request {@link Object} along with an
 * instance of {@link BiFunction} that can be used to invoke any
 * method on the {@link LdapOperations} instance
 */
public enum LdapOperation {
    SEARCH, BIND, UNBIND, AUTHENTICATE, MODIFY_ATTRIBUTES, FUNCTION_DRIVEN
}
