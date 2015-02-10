package org.apache.camel.maven;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class PackageHelperTest {

  @Test
  public void testFileToString() throws Exception {
    assertEquals("dk19i21)@+#(OR", PackageHelper.fileToString(new File(
        this.getClass().getClassLoader().getResource("filecontent/a.txt").getFile())));
  }

  @Test
  public void testFindJsonFiles() throws Exception {
    Map<String, File> jsonFiles = PackageHelper.findJsonFiles(new File(
        this.getClass().getClassLoader().getResource("json").getFile()));

    assertTrue("Files a.json must be found", jsonFiles.containsKey("a"));
    assertTrue("Files b.json must be found", jsonFiles.containsKey("b"));
    assertFalse("File c.txt must not be found", jsonFiles.containsKey("c"));
  }
}