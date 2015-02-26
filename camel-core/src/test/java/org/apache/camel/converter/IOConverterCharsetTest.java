package org.apache.camel.converter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.apache.camel.ContextTestSupport;

public class IOConverterCharsetTest extends ContextTestSupport {

	public void testToInputStreamFileWithCharsetUTF8() throws Exception {
		File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
		InputStream in = IOConverter.toInputStream(file, "UTF-8");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String line = reader.readLine();
		String naiveLine = naiveReader.readLine();
		assertEquals(naiveLine, line);
		assertEquals("Götzendämmerung,Joseph und seine Brüder", line);
		reader.close();
		naiveReader.close();
	}

	public void testToInputStreamFileWithCharsetLatin1() throws Exception {
		File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
		InputStream in = IOConverter.toInputStream(file, "ISO-8859-1");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
		String line = reader.readLine();
		String naiveLine = naiveReader.readLine();
		assertEquals(naiveLine, line);
		assertEquals("Götzendämmerung,Joseph und seine Brüder", line);
		reader.close();
		naiveReader.close();
	}
	
	public void testToInputStreamFileDirectByteDumpWithCharsetLatin1() throws Exception {
		File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
		InputStream in = IOConverter.toInputStream(file, "ISO-8859-1");
		InputStream naiveIn = new FileInputStream(file);
		byte[] bytes = new byte[8192];
		in.read(bytes);
		byte[] naiveBytes = new byte[8192];
		naiveIn.read(naiveBytes);
		assertFalse("both input streams deliver the same byte sequence", Arrays.equals(naiveBytes, bytes));
		in.close();
		naiveIn.close();
	}
	
	public void testToReaderFileWithCharsetUTF8() throws Exception {
		File file = new File("src/test/resources/org/apache/camel/converter/german.utf-8.txt");
		BufferedReader reader = IOConverter.toReader(file, "UTF-8");
		BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		String line = reader.readLine();
		String naiveLine = naiveReader.readLine();
		assertEquals(naiveLine, line);
		assertEquals("Götzendämmerung,Joseph und seine Brüder", line);
		reader.close();
		naiveReader.close();
	}

	public void testToReaderFileWithCharsetLatin1() throws Exception {
		File file = new File("src/test/resources/org/apache/camel/converter/german.iso-8859-1.txt");
		BufferedReader reader = IOConverter.toReader(file, "ISO-8859-1");
		BufferedReader naiveReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
		String line = reader.readLine();
		String naiveLine = naiveReader.readLine();
		assertEquals(naiveLine, line);
		assertEquals("Götzendämmerung,Joseph und seine Brüder", line);
		reader.close();
		naiveReader.close();
	}
	
}
