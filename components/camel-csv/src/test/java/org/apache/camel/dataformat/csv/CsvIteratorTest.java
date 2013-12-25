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
package org.apache.camel.dataformat.csv;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.NoSuchElementException;

import mockit.Expectations;
import mockit.Injectable;
import org.apache.commons.csv.CSVParser;
import org.junit.Assert;
import org.junit.Test;

public class CsvIteratorTest {

    public static final String HDD_CRASH = "HDD crash";

    @Test
    public void closeIfError(
            @Injectable final  InputStreamReader reader,
            @Injectable final CSVParser parser) throws IOException {
        new Expectations() {
            {
                parser.getLine();
                result = new String[] {"1"};

                parser.getLine();
                result = new String[] {"2"};

                parser.getLine();
                result = new IOException(HDD_CRASH);

                // The reader will be closed when there is nothing left
                reader.close();
            }
        };

        @SuppressWarnings("resource")
        CsvIterator iterator = new CsvIterator(parser, reader);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(Arrays.asList("1"), iterator.next());
        Assert.assertTrue(iterator.hasNext());

        try {
            iterator.next();
            Assert.fail("exception expected");
        } catch (IllegalStateException e) {
            Assert.assertEquals(HDD_CRASH, e.getCause().getMessage());
        }

        Assert.assertFalse(iterator.hasNext());

        try {
            iterator.next();
            Assert.fail("exception expected");
        } catch (NoSuchElementException e) {
            // okay
        }
    }

    @Test
    public void normalCycle(@Injectable final InputStreamReader reader,
                            @Injectable final CSVParser parser) throws IOException {
        new Expectations() {
            {
                parser.getLine();
                result = new String[] {"1"};

                parser.getLine();
                result = new String[] {"2"};

                parser.getLine();
                result = null;

                // The reader will be closed when there is nothing left
                reader.close();
            }
        };
       
        @SuppressWarnings("resource")
        CsvIterator iterator = new CsvIterator(parser, reader);
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(Arrays.asList("1"), iterator.next());

        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals(Arrays.asList("2"), iterator.next());

        Assert.assertFalse(iterator.hasNext());

        try {
            iterator.next();
            Assert.fail("exception expected");
        } catch (NoSuchElementException e) {
            // okay
        }
        
    }
}
