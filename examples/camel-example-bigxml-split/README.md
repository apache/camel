# Splitting big XML payloads

### Introduction
This example shows how to deal with big XML files in Camel.  

The XPath tokenizer will load the entire XML content into memory, so it's not well suited for very big XML payloads.  
Instead you can use the XML or StAX tokenizers to efficiently iterate the XML payload in a streamed fashion.  
For more information please read the [official documentation](http://camel.apache.org/splitter.html).

There are 2 tests: 

1. `testXmlTokenizer` : this is faster, but not able to handle complex XML structures (i.e. nested naming clash)
2. `testStaxTokenizer` : the best option, still fast and process messages through a SAX ContentHandler

The test XML file is built once beforehand using JUnit `@BeforeClass`.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<records xmlns="http://fvaleri.it/records">
    <record>
        <key>0</key>
        <value>The quick brown fox jumps over the lazy dog</value>
    </record>
</records>
```

We want to test time and memory performance with different payloads, then numOfRecords and maxWaitTime can be specified.  
The available memory is restricted to 20 MB (see `pom.xml`), there is no cache enabled, no parallel processing 
and no mock endpoints that store exchanges into memory. 

### Build and run
Set DEBUG log level to see more details.
```sh
mvn clean test
```

You can also test a single tokenizer.
```sh
mvn clean test -Dtest=BigXmlSplitTest#testXmlTokenizer
```

### Test results
Tested on MacBook Pro 2,8 GHz Intel Core i7; 16 GB 2133 MHz LPDDR3; Java 1.8.0_181.

tokenizer | numOfRecords | maxWaitTime (ms) | XML size (kB) | time (ms) | memory (kB)
--- | --- | --- | --- | --- | --- 
XML | 40000 | 5000 | 3543 | 3991 | 107931
StAX | 40000 | 5000 | 3543 | 3240 | 110883
XML | 1000000 | 20000 | 89735 | - | -
StAX | 1000000 | 20000 | 89735 | - | -
XML | 15000000 | 180000 | 1366102 | - | -
StAX | 15000000 | 180000 | 1366102 | - | -

**Memory leak located in the ReactiveHelper class (back LinkedList).**
XML tokenizer with 40k records (-Xmx256m): we have **3MB** on `2.23.0` vs **108MB** on `3.0.0-SNAPSHOT`.  
Same for the StAX tokenizer (with more records we soon have `OutOfMemory Java heap space`).

### Forum, Help, etc
If you hit an problems please let us know on the Camel Forums
<http://camel.apache.org/discussion-forums.html>

Please help us make Apache Camel better - we appreciate any feedback you may have. Enjoy!

The Camel riders!
