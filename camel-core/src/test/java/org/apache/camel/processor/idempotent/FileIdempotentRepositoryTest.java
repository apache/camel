package org.apache.camel.processor.idempotent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileIdempotentRepositoryTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();
	
	private FileIdempotentRepository fileIdempotentRepository = new FileIdempotentRepository();
	private List<String> files;
	
	@Before
	public void setup() throws IOException {
		files = Arrays.asList(
				"file1.txt.20171123",
				"file2.txt.20171123",
				"file1.txt.20171124",
				"file3.txt.20171125",
				"file2.txt.20171126",
				"fixed.income.lamr.out.20171126",
				"pricing.px.20171126",
				"test.out.20171126",
				"processing.source.lamr.out.20171126");
		this.fileIdempotentRepository = new FileIdempotentRepository();
	}
	
	@Test
	public void testTrunkStore() throws URISyntaxException, IOException {
		//given
		File fileStore = temporaryFolder.newFile();
		fileIdempotentRepository.setFileStore(fileStore);
		fileIdempotentRepository.setCacheSize(10);
		files.forEach(e -> fileIdempotentRepository.add(e));

		//when
		fileIdempotentRepository.trunkStore();

		//then
		Stream<String> fileContent = Files.lines(fileStore.toPath());
		List<String> fileEntries = fileContent.collect(Collectors.toList());
		fileContent.close();
		//expected order
		assertThat(fileEntries, contains(
				"file1.txt.20171123", 
				"file2.txt.20171123",
				"file1.txt.20171124",
				"file3.txt.20171125",
				"file2.txt.20171126",
				"fixed.income.lamr.out.20171126",
				"pricing.px.20171126",
				"test.out.20171126",
				"processing.source.lamr.out.20171126"));

		//current order
/*		assertThat(fileEntries, contains(
				"processing.source.lamr.out.20171126",
				"test.out.20171126",
				"fixed.income.lamr.out.20171126",
				"pricing.px.20171126",
				"file1.txt.20171123", 
				"file2.txt.20171123",
				"file1.txt.20171124",
				"file3.txt.20171125",
				"file2.txt.20171126"));*/
		
	}

}
