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

package org.apache.camel.component.netty;

import java.io.Serializable;

public class Poetry implements Serializable {
    private static final long serialVersionUID = 1L;
    private String poet = "?";
    private String poem = "ONCE in the dream of a night I stood\n" 
                          + "Lone in the light of a magical wood,\n"  
                          + "Soul-deep in visions that poppy-like sprang;\n"  
                          + "And spirits of Truth were the birds that sang,\n"  
                          + "And spirits of Love were the stars that glowed,\n" 
                          + "And spirits of Peace were the streams that flowed\n"  
                          + "In that magical wood in the land of sleep." 
                          + "\n" 
                          + "Lone in the light of that magical grove,\n"  
                          + "I felt the stars of the spirits of Love\n" 
                          + "Gather and gleam round my delicate youth,\n" 
                          + "And I heard the song of the spirits of Truth;\n" 
                          + "To quench my longing I bent me low\n"  
                          + "By the streams of the spirits of Peace that flow\n" 
                          + "In that magical wood in the land of sleep.";

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
