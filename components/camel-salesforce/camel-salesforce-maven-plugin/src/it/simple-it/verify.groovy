// assert that the generated files directory exists
File sourceDir = new File( basedir, "target/generated-sources/camel-salesforce" );

assert sourceDir.isDirectory()
