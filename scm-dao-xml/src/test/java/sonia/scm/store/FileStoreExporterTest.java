/*
 * MIT License
 *
 * Copyright (c) 2020-present Cloudogu GmbH and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package sonia.scm.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sonia.scm.repository.Repository;
import sonia.scm.repository.RepositoryLocationResolver;
import sonia.scm.repository.RepositoryTestData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileStoreExporterTest {

  private static final Repository REPOSITORY = RepositoryTestData.create42Puzzle();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RepositoryLocationResolver resolver;

  @InjectMocks
  private FileStoreExporter fileStoreExporter;

  private Path storePath;

  @BeforeEach
  void setUpStorePath(@TempDir Path temp) {
    storePath = temp.resolve("store");
    when(resolver.forClass(Path.class).getLocation(REPOSITORY.getId())).thenReturn(temp);
  }

  @Test
  void shouldReturnEmptyList(@TempDir Path temp) {
    when(resolver.forClass(Path.class).getLocation(REPOSITORY.getId())).thenReturn(temp);

    List<ExportableStore> exportableStores = fileStoreExporter.listExportableStores(REPOSITORY);
    assertThat(exportableStores).isEmpty();
  }

  @Test
  void shouldReturnConfigStores() throws IOException {
    createFile(StoreType.CONFIG.getValue(), "config.xml")
      .withContent("<?xml version=\"1.0\" ?>", "<data>", "some arbitrary content", "</data>");

    List<ExportableStore> exportableStores = fileStoreExporter.listExportableStores(REPOSITORY);

    assertThat(exportableStores).hasSize(1);
    assertThat(exportableStores.stream().filter(e -> e.getMetaData().getType().equals(StoreType.CONFIG))).hasSize(1);
  }

  @Test
  void shouldReturnConfigEntryStores() throws IOException {
    createFile(StoreType.CONFIG.getValue(), "config-entry.xml")
      .withContent("<?xml version=\"1.0\" ?>", "<configuration type=\"config-entry\">", "</configuration>");

    List<ExportableStore> exportableStores = fileStoreExporter.listExportableStores(REPOSITORY);

    assertThat(exportableStores).hasSize(1);
    assertThat(exportableStores.stream().filter(e -> e.getMetaData().getType().equals(StoreType.CONFIG_ENTRY))).hasSize(1);
  }

  @Test
  void shouldReturnDataStores() throws IOException {
    createFile(StoreType.DATA.getValue(), "ci", "data.xml");
    createFile(StoreType.DATA.getValue(), "jenkins", "data.xml");

    List<ExportableStore> exportableStores = fileStoreExporter.listExportableStores(REPOSITORY);

    assertThat(exportableStores).hasSize(2);
    assertThat(exportableStores.stream().filter(e -> e.getMetaData().getType().equals(StoreType.DATA))).hasSize(2);
  }

  private FileWriter createFile(String... names) throws IOException {
    Path file = Arrays.stream(names).map(Paths::get).reduce(Path::resolve).map(storePath::resolve).orElse(storePath);
    Files.createDirectories(file.getParent());
    if (!Files.exists(file)) {
      Files.createFile(file);
    }
    return new FileWriter(file);
  }

  private static class FileWriter {

    private final Path file;

    private FileWriter(Path file) {
      this.file = file;
    }

    void withContent(String... content) throws IOException {
      Files.write(file, Arrays.asList(content));
    }
  }
}
