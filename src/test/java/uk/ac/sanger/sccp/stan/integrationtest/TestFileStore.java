package uk.ac.sanger.sccp.stan.integrationtest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.config.StanFileConfig;
import uk.ac.sanger.sccp.stan.model.*;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests storing files.
 * @see StanFile
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestFileStore {
    @Autowired
    private StanFileConfig stanFileConfig;
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Test
    @Transactional
    public void testFileStore() throws Exception {
        Path directory = Paths.get(stanFileConfig.getRoot(), stanFileConfig.getDir());
        if (!Files.isDirectory(directory)) {
            Files.createDirectories(directory);
        }
        Path local = Files.createTempFile("stanfile", ".txt");
        Files.write(local, List.of("Alpha", "Beta"));
        Work work = entityCreator.createWork(null, null, null, null);
        String workNumber = work.getWorkNumber();
        assertThat(listFiles(workNumber)).isEmpty();
        String username = "user1";
        User user = entityCreator.createUser(username);
        tester.setUser(user);

        final String filename = "stanfile.txt";
        final String fileContent = "Hello\nworld";
        String downloadUrl = upload(workNumber, filename, fileContent);

        assertEquals(fileContent, download(downloadUrl, filename));

        var filesData = listFiles(workNumber);
        assertThat(filesData).hasSize(1);
        assertFileData(filesData.get(0), filename, downloadUrl, username, workNumber);

        String fileContent2 = "Alabama\nAlaska";
        final String filename2 = "stanfile2.txt";
        String url2 = upload(workNumber, filename2, fileContent2);

        assertEquals(fileContent2, download(url2, filename2));

        filesData = listFiles(workNumber);
        assertThat(filesData).hasSize(2);
        if (filesData.get(0).get("name").equals(filename2)) {
            filesData = List.of(filesData.get(1), filesData.get(0));
        }

        assertFileData(filesData.get(0), filename, downloadUrl, username, workNumber);
        assertFileData(filesData.get(1), filename2, url2, username, workNumber);

        String fileContentB = "Goodbye\nWorld";
        downloadUrl = upload(workNumber, filename, fileContentB);

        assertEquals(fileContentB, download(downloadUrl, filename));
        filesData = listFiles(workNumber);
        assertThat(filesData).hasSize(2);
        if (filesData.get(0).get("name").equals(filename2)) {
            filesData = List.of(filesData.get(1), filesData.get(0));
        }

        assertFileData(filesData.get(0), filename, downloadUrl, username, workNumber);
        assertFileData(filesData.get(1), filename2, url2, username, workNumber);

        deleteTestFiles(directory);
    }

    private List<Map<String, ?>> listFiles(String workNumber) throws Exception {
        String query = tester.readGraphQL("listfiles.graphql").replace("SGP1", workNumber);
        Object result = tester.post(query);
        return chainGet(result, "data", "listFiles");
    }

    private String upload(String workNumber, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, MediaType.TEXT_PLAIN_VALUE, content.getBytes()
        );
        return tester.getMockMvc().perform(
                multipart("/files").file(file).queryParam("workNumber", workNumber)
        ).andExpect(status().isCreated()).andReturn().getResponse().getHeader("location");
    }

    private String download(String downloadUrl, String filename) throws Exception {
        var r = tester.getMockMvc().perform(MockMvcRequestBuilders.get(downloadUrl)).andExpect(status().isOk())
                .andReturn()
                .getResponse();
        assertThat(r.getHeader("Content-Disposition")).contains(filename);
        return r.getContentAsString();
    }

    private void assertFileData(Map<String, ?> data, String filename, String url, String username, String workNumber) {
        assertNotNull(data.get("created"));
        assertEquals(filename, data.get("name"));
        assertEquals(url, data.get("url"));
        assertEquals(username, chainGet(data, "user", "username"));
        assertEquals(workNumber, chainGet(data, "work", "workNumber"));
    }

    private void deleteTestFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            Iterable<Path> pathIter = files::iterator;
            for (Path path : pathIter) {
                Files.delete(path);
            }
        }
    }
}
