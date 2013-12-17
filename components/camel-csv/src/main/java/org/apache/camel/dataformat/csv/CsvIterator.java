package org.apache.camel.dataformat.csv;

import org.apache.camel.util.IOHelper;
import org.apache.commons.csv.CSVParser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 */
public class CsvIterator implements Iterator<List<String>>, Closeable {

    private final CSVParser parser;
    private final InputStreamReader in;
    private String[] line;

    public CsvIterator(CSVParser parser, InputStreamReader in)
            throws IOException
    {
        this.parser = parser;
        this.in = in;
        line = parser.getLine();
    }

    @Override
    public boolean hasNext() {
        return line != null;
    }

    @Override
    public List<String> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        List<String> result = Arrays.asList(line);
        try {
            line = parser.getLine();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (line == null) {
            IOHelper.close(in);
        }
        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
