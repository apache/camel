# Splitting big XML payloads
This example shows how to deal with big XML files in Camel.  

The XPath tokenizer will load the entire XML content into memory, so it's not well suited for very big XML payloads.  
Instead you can use the StAX or XML tokenizers to efficiently iterate the XML payload in a streamed fashion.  
For more information please read the [official documentation](http://camel.apache.org/splitter.html).

There are 2 tests:

1. `StaxTokenizerTest` : requires using JAXB and process messages using a SAX ContentHandler
2. `XmlTokenizerTest` : easier to use but can't handle complex XML structures (i.e. nested naming clash)

The test XML contains a simple collection of records.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<records xmlns="http://fvaleri.it/records">
    <record>
        <key>0</key>
        <value>The quick brown fox jumps over the lazy dog</value>
    </record>
</records>
```

You can customize numOfRecords and maxWaitTime to test time performance with different payloads.  
The available memory is restricted to 20 MB (see `pom.xml`), there is no cache enabled, no parallel processing 
and no mock endpoints that store exchanges into memory. 

### Build and run
The test XML file is built once beforehand using `@BeforeClass`.
```sh
mvn clean test
```

### Test results
Tested on MacBook Pro 2,8 GHz Intel Core i7; 16 GB 2133 MHz LPDDR3; Java 1.8.0_181.

tokenizer | numOfRecords | maxWaitTime (ms) | XML size (kB) | time (ms) 
--- | --- | --- | --- | --- | --- 
StAX | 40000 | 5000 | 3543 | 3081
XML | 40000 | 5000 | 3543 | 2567
StAX | 1000000 | 20000 | 89735 | 11535
XML | 1000000 | 20000 | 89735 | 11431
StAX | 15000000 | 180000 | 1366102 | 131786
XML | 15000000 | 180000 | 1366102 | 137322

### Forum, Help, etc
If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
