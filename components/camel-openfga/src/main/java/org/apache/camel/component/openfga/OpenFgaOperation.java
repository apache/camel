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
package org.apache.camel.component.openfga;

/**
 * The operations the {@code openfga} producer supports.
 * <p/>
 * The operation is part of the endpoint URI and is deliberately not overridable by a message header: the operation
 * decides whether the endpoint asks a question or changes the relationship graph, which is not a choice an inbound
 * message may make.
 */
public enum OpenFgaOperation {

    /**
     * Ask whether the subject may use the relation on the object. The verdict is reported on the
     * {@link OpenFgaConstants#ALLOWED} header and the body is left untouched.
     */
    check,

    /**
     * Ask the same question for many objects at once, taking the object identifiers from the body and returning the
     * ones the check allowed.
     */
    batchCheck,

    /**
     * List the objects of a type that the subject can reach through the relation.
     */
    listObjects,

    /**
     * List which of the configured relations the subject holds on the object.
     */
    listRelations,

    /**
     * List the subjects that hold the relation on the object.
     */
    listUsers,

    /**
     * Grant access by writing relationship tuples.
     */
    writeTuples,

    /**
     * Revoke access by deleting relationship tuples.
     */
    deleteTuples
}
