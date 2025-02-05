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
package org.apache.camel.component.neo4j;

/**
 * Class that represents the embedding to persist when using LangChain4j - The names of the properties correspond to the
 * ones in LangChain4j project for compatibility.
 */
public class Neo4jEmbedding {
    private String id;

    private String text;

    private float[] vectors;

    public Neo4jEmbedding(String id, String text, float[] vectors) {
        this.id = id;
        this.text = text;
        this.vectors = vectors;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public float[] getVectors() {
        return vectors;
    }
}
