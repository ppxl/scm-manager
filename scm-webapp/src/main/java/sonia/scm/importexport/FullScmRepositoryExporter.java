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

package sonia.scm.importexport;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import sonia.scm.ContextEntry;
import sonia.scm.repository.Repository;
import sonia.scm.repository.api.ExportFailedException;
import sonia.scm.repository.api.RepositoryService;
import sonia.scm.repository.api.RepositoryServiceFactory;
import sonia.scm.repository.work.WorkdirProvider;
import sonia.scm.util.IOUtil;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FullScmRepositoryExporter {

  static final String SCM_ENVIRONMENT_FILE_NAME = "scm-environment.xml";
  static final String METADATA_FILE_NAME = "metadata.xml";
  static final String STORE_DATA_FILE_NAME = "store-data.tar";
  private final EnvironmentInformationXmlGenerator environmentGenerator;
  private final RepositoryMetadataXmlGenerator metadataGenerator;
  private final RepositoryServiceFactory serviceFactory;
  private final TarArchiveRepositoryStoreExporter storeExporter;
  private final WorkdirProvider workdirProvider;

  @Inject
  public FullScmRepositoryExporter(EnvironmentInformationXmlGenerator environmentGenerator,
                                   RepositoryMetadataXmlGenerator metadataGenerator,
                                   RepositoryServiceFactory serviceFactory,
                                   TarArchiveRepositoryStoreExporter storeExporter, WorkdirProvider workdirProvider) {
    this.environmentGenerator = environmentGenerator;
    this.metadataGenerator = metadataGenerator;
    this.serviceFactory = serviceFactory;
    this.storeExporter = storeExporter;
    this.workdirProvider = workdirProvider;
  }

  public void export(Repository repository, OutputStream outputStream) {
    try (
      RepositoryService service = serviceFactory.create(repository);
      BufferedOutputStream bos = new BufferedOutputStream(outputStream);
      GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
      TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos);
    ) {
      writeEnvironmentData(taos);
      writeMetadata(repository, taos);
      writeRepository(service, taos);
      writeStoreData(repository, taos);
      taos.finish();
    } catch (IOException e) {
      throw new ExportFailedException(
        ContextEntry.ContextBuilder.entity(repository).build(),
        "Could not export repository with metadata",
        e
      );
    }
  }

  private void writeEnvironmentData(TarArchiveOutputStream taos) throws IOException {
    byte[] envBytes = environmentGenerator.generate();
    TarArchiveEntry entry = new TarArchiveEntry(SCM_ENVIRONMENT_FILE_NAME);
    entry.setSize(envBytes.length);
    taos.putArchiveEntry(entry);
    taos.write(envBytes);
    taos.closeArchiveEntry();
  }

  private void writeMetadata(Repository repository, TarArchiveOutputStream taos) throws IOException {
    byte[] metadataBytes = metadataGenerator.generate(repository);
    TarArchiveEntry entry = new TarArchiveEntry(METADATA_FILE_NAME);
    entry.setSize(metadataBytes.length);
    taos.putArchiveEntry(entry);
    taos.write(metadataBytes);
    taos.closeArchiveEntry();
  }

  private void writeRepository(RepositoryService service, TarArchiveOutputStream taos) throws IOException {
    File newWorkdir = workdirProvider.createNewWorkdir(service.getRepository().getId());
    try {
      File repositoryFile = Files.createFile(Paths.get(newWorkdir.getPath(), "repository")).toFile();
      try (FileOutputStream repositoryFos = new FileOutputStream(repositoryFile)) {
        service.getBundleCommand().bundle(repositoryFos);
      }
      TarArchiveEntry entry = new TarArchiveEntry(createRepositoryEntryName(service));
      entry.setSize(repositoryFile.length());
      taos.putArchiveEntry(entry);
      Files.copy(repositoryFile.toPath(), taos);
      taos.closeArchiveEntry();
    } finally {
      IOUtil.deleteSilently(newWorkdir);
    }
  }

  private String createRepositoryEntryName(RepositoryService service) {
    return String.format("%s.%s", service.getRepository().getName(), service.getBundleCommand().getFileExtension());
  }

  private void writeStoreData(Repository repository, TarArchiveOutputStream taos) throws IOException {
    File newWorkdir = workdirProvider.createNewWorkdir(repository.getId());
    try {
      File metadata = Files.createFile(Paths.get(newWorkdir.getPath(), "metadata")).toFile();
      try (FileOutputStream metadataFos = new FileOutputStream(metadata)) {
        storeExporter.export(repository, metadataFos);
      }
      TarArchiveEntry entry = new TarArchiveEntry(STORE_DATA_FILE_NAME);
      entry.setSize(metadata.length());
      taos.putArchiveEntry(entry);
      Files.copy(metadata.toPath(), taos);
      taos.closeArchiveEntry();
    } finally {
      IOUtil.deleteSilently(newWorkdir);
    }
  }
}
