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
package org.apache.camel.component.djl;

import java.util.List;

import ai.djl.modality.Classifications;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.PointerUtils;
import net.sf.extjwnl.data.list.PointerTargetNodeList;
import net.sf.extjwnl.dictionary.Dictionary;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;

/**
 * A utility bean class for handling ImageNet (https://image-net.org/) classifications.
 */
public class ImageNetUtil {

    public ImageNetUtil() {
    }

    public void extractClassName(Exchange exchange) {
        Classifications body = exchange.getMessage().getBody(Classifications.class);
        String className = body.best().getClassName().split(",")[0].split(" ", 2)[1];
        exchange.getMessage().setBody(className);
    }

    public void addHypernym(Exchange exchange) throws Exception {
        String className = exchange.getMessage().getBody(String.class);
        Dictionary dic = Dictionary.getDefaultResourceInstance();
        IndexWord word = dic.getIndexWord(POS.NOUN, className);
        if (word == null) {
            throw new RuntimeCamelException("Word not found: " + className);
        }
        PointerTargetNodeList hypernyms = PointerUtils.getDirectHypernyms(word.getSenses().get(0));
        String hypernym = hypernyms.stream()
                .map(h -> h.getSynset().getWords().get(0).getLemma())
                .findFirst().orElse(className);
        exchange.getMessage().setBody(List.of(className, hypernym));
    }
}
