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
package org.apache.camel.component.printer;

import javax.print.attribute.standard.MediaSizeName;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MediaSizeAssignerTest {

    private final MediaSizeAssigner assigner = new MediaSizeAssigner();

    @Test
    public void testLowercaseNames() {
        assertSame(MediaSizeName.ISO_A4, assigner.selectMediaSizeName("iso_a4"));
        assertSame(MediaSizeName.JIS_B4, assigner.selectMediaSizeName("jis_b4"));
        assertSame(MediaSizeName.NA_LETTER, assigner.selectMediaSizeName("na_letter"));
        assertSame(MediaSizeName.EXECUTIVE, assigner.selectMediaSizeName("executive"));
    }

    @Test
    public void testNamesAreCaseInsensitive() {
        // the mediaSize option documents that the value's case is ignored
        assertSame(MediaSizeName.ISO_A4, assigner.selectMediaSizeName("ISO_A4"));
        assertSame(MediaSizeName.JIS_B4, assigner.selectMediaSizeName("JIS_B4"));
        assertSame(MediaSizeName.NA_LETTER, assigner.selectMediaSizeName("NA_LETTER"));
        assertSame(MediaSizeName.EXECUTIVE, assigner.selectMediaSizeName("Executive"));
    }

    @Test
    public void testHyphenIsAcceptedForUnderscore() {
        // na-letter is the documented default value of the mediaSize option
        assertSame(MediaSizeName.NA_LETTER, assigner.selectMediaSizeName("na-letter"));
        assertSame(MediaSizeName.ISO_A4, assigner.selectMediaSizeName("iso-a4"));
        assertSame(MediaSizeName.NA_NUMBER_10_ENVELOPE, assigner.selectMediaSizeName("na-number-10-envelope"));
    }

    @Test
    public void testSizeOutsideTheFamilyItsPrefixSuggests() {
        // iso_designated_long and iso_c3 are not part of the ISO A/B/C series tables
        assertSame(MediaSizeName.ISO_DESIGNATED_LONG, assigner.selectMediaSizeName("iso_designated_long"));
        assertSame(MediaSizeName.ISO_DESIGNATED_LONG, assigner.selectMediaSizeName("ISO_DESIGNATED_LONG"));
    }

    @Test
    public void testUnknownSizeIsRejected() {
        IllegalArgumentException e
                = assertThrows(IllegalArgumentException.class, () -> assigner.selectMediaSizeName("no_such_size"));
        assertEquals("Unknown mediaSize: no_such_size", e.getMessage());

        // an unmatched value must not leak the result of an earlier lookup either
        assigner.selectMediaSizeName("iso_a4");
        assertThrows(IllegalArgumentException.class, () -> assigner.selectMediaSizeName("iso_a99"));
    }
}
