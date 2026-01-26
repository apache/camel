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
package org.apache.camel.component.netty;

import java.io.Serializable;

public class Poetry implements Serializable {
    private static final long serialVersionUID = 1L;
    private String poet = "?";
    private String poem = """
            ONCE in the dream of a night I stood
            Lone in the light of a magical wood,
            Soul-deep in visions that poppy-like sprang;
            And spirits of Truth were the birds that sang,
            And spirits of Love were the stars that glowed,
            And spirits of Peace were the streams that flowed
            In that magical wood in the land of sleep.
            Lone in the light of that magical grove,
            I felt the stars of the spirits of Love
            Gather and gleam round my delicate youth,
            And I heard the song of the spirits of Truth;
            To quench my longing I bent me low
            By the streams of the spirits of Peace that flow
            In that magical wood in the land of sleep.""";

    public String getPoet() {
        return poet;
    }

    public void setPoet(String poet) {
        this.poet = poet;
    }

    public String getPoem() {
        return poem;
    }

    public void setPoem(String poem) {
        this.poem = poem;
    }

}
