package org.apache.camel.dataformat.csv;

import mockit.Expectations;
import mockit.Injectable;
import org.apache.commons.csv.CSVParser;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 */
public class CsvIteratorTest {

    public static final String HDD_CRASH = "HDD crash";

    @Test
    public void closeIfError(
            final @Injectable InputStreamReader reader,
            final @Injectable CSVParser parser)
            throws IOException
    {
        new Expectations() {
            {
                parser.getLine();
                result = new String[] { "1" };

                parser.getLine();
                result = new String[] { "2" };

                parser.getLine();
                result = new IOException(HDD_CRASH);

                reader.close();
            }
        };

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
    public void normalCycle(final @Injectable InputStreamReader reader,
                            final @Injectable CSVParser parser)
            throws IOException
    {
        new Expectations() {
            {
                parser.getLine();
                result = new String[] { "1" };

                parser.getLine();
                result = new String[] { "2" };

                parser.getLine();
                result = null;

                reader.close();
            }
        };

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
