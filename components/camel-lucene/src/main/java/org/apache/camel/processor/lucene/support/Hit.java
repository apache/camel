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
package org.apache.camel.processor.lucene.support;

import java.io.Serializable;

import org.apache.lucene.document.Document;

public class Hit implements Serializable {
    private static final long serialVersionUID = 1L;

    private int hitLocation;
    private float score;
    private String data;
    private transient Document document;

    public int getHitLocation() {
        return hitLocation;
    }

    public void setHitLocation(int value) {
        this.hitLocation = value;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float value) {
        this.score = value;
    }

    public String getData() {
        return data;
    }

    public void setData(String value) {
        this.data = value;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    @Override
    public String toString() {
        return "Hit[location=" + hitLocation + ", score=" + score + ", data=" + data + "]";
    }
}
