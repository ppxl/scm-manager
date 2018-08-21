package sonia.scm.it;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import org.apache.http.HttpStatus;
import sonia.scm.repository.Changeset;
import sonia.scm.repository.Person;
import sonia.scm.repository.client.api.ClientCommand;
import sonia.scm.repository.client.api.RepositoryClient;
import sonia.scm.repository.client.api.RepositoryClientFactory;
import sonia.scm.web.VndMediaType;

import java.io.File;
import java.io.IOException;

import static sonia.scm.it.RestUtil.ADMIN_PASSWORD;
import static sonia.scm.it.RestUtil.ADMIN_USERNAME;
import static sonia.scm.it.RestUtil.given;

public class RepositoryUtil {

  private static final RepositoryClientFactory REPOSITORY_CLIENT_FACTORY = new RepositoryClientFactory();

  private final RepositoryClient repositoryClient;
  private final File folder;

  RepositoryUtil(String repositoryType, File folder) throws IOException {
    this.repositoryClient = createRepositoryClient(repositoryType, folder);
    this.folder = folder;
  }

  static RepositoryClient createRepositoryClient(String repositoryType, File folder) throws IOException {
    String httpProtocolUrl = given(VndMediaType.REPOSITORY)

      .when()
      .get(TestData.getDefaultRepositoryUrl(repositoryType))

      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .path("_links.httpProtocol.href");


    return REPOSITORY_CLIENT_FACTORY.create(repositoryType, httpProtocolUrl, ADMIN_USERNAME, ADMIN_PASSWORD, folder);
  }

  void createAndCommitFile(String fileName, String content) throws IOException {
    Files.write(content, new File(folder, fileName), Charsets.UTF_8);
    repositoryClient.getAddCommand().add(fileName);
    commit("added " + fileName);
  }

  Changeset commit(String message) throws IOException {
    Changeset changeset = repositoryClient.getCommitCommand().commit(
      new Person("scmadmin", "scmadmin@scm-manager.org"), message
    );
    if (repositoryClient.isCommandSupported(ClientCommand.PUSH)) {
      repositoryClient.getPushCommand().push();
    }
    return changeset;
  }
}
