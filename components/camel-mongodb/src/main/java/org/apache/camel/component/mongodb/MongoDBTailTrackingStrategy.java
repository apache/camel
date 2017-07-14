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
package org.apache.camel.component.mongodb;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * This class contains different methods for extracting and saving tail tracking information.
 */
public interface MongoDBTailTrackingStrategy {

    /**
     * Extracts the last tracking value using the field name or an expression.
     * @param o The object retrieved by the trailing process.
     * @param increasingField The field name or an expression used to extract the last value.
     * @return an object representing the last tracking value in a MongoDB collection.
     */
    Object extractLastVal(DBObject o, String increasingField);

    /**
     * Creates an object to be used in a query using the last tracking value.
     * @param lastVal The last tracking value.
     * @param increasingField The field name or an expression used to extract the last value.
     * @return the object to be used in a MongoDB query.
     */
    BasicDBObject createQuery(Object lastVal, String increasingField);
}
