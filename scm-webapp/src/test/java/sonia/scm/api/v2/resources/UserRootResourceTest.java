package sonia.scm.api.v2.resources;

import com.github.sdorra.shiro.ShiroRule;
import com.github.sdorra.shiro.SubjectAware;
import com.google.common.io.Resources;
import com.google.inject.util.Providers;
import org.apache.shiro.authc.credential.PasswordService;
import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import sonia.scm.ContextEntry;
import sonia.scm.NotFoundException;
import sonia.scm.PageResult;
import sonia.scm.security.PermissionAssigner;
import sonia.scm.security.PermissionDescriptor;
import sonia.scm.user.ChangePasswordNotAllowedException;
import sonia.scm.user.User;
import sonia.scm.user.UserManager;
import sonia.scm.web.VndMediaType;

import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static sonia.scm.api.v2.resources.DispatcherMock.createDispatcher;

@SubjectAware(
  username = "trillian",
  password = "secret",
  configuration = "classpath:sonia/scm/repository/shiro.ini"
)
public class UserRootResourceTest {

  @Rule
  public ShiroRule shiro = new ShiroRule();

  private Dispatcher dispatcher;

  private final ResourceLinks resourceLinks = ResourceLinksMock.createMock(URI.create("/"));

  @Mock
  private PasswordService passwordService;
  @Mock
  private UserManager userManager;
  @Mock
  private PermissionAssigner permissionAssigner;
  @InjectMocks
  private UserDtoToUserMapperImpl dtoToUserMapper;
  @InjectMocks
  private UserToUserDtoMapperImpl userToDtoMapper;
  @InjectMocks
  private PermissionCollectionToDtoMapper permissionCollectionToDtoMapper;

  private ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
  private User originalUser;

  @Before
  public void prepareEnvironment() throws Exception {
    initMocks(this);
    originalUser = createDummyUser("Neo");
    when(userManager.create(userCaptor.capture())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(userManager.isTypeDefault(userCaptor.capture())).thenCallRealMethod();
    doNothing().when(userManager).modify(userCaptor.capture());
    doNothing().when(userManager).delete(userCaptor.capture());
    when(userManager.getDefaultType()).thenReturn("xml");

    UserCollectionToDtoMapper userCollectionToDtoMapper = new UserCollectionToDtoMapper(userToDtoMapper, resourceLinks);
    UserCollectionResource userCollectionResource = new UserCollectionResource(userManager, dtoToUserMapper,
      userCollectionToDtoMapper, resourceLinks, passwordService);
    UserPermissionResource userPermissionResource = new UserPermissionResource(permissionAssigner, permissionCollectionToDtoMapper);
    UserResource userResource = new UserResource(dtoToUserMapper, userToDtoMapper, userManager, passwordService, userPermissionResource);
    UserRootResource userRootResource = new UserRootResource(Providers.of(userCollectionResource),
      Providers.of(userResource));

    dispatcher = createDispatcher(userRootResource);
  }

  @Test
  public void shouldCreateFullResponseForAdmin() throws URISyntaxException, UnsupportedEncodingException {
    MockHttpRequest request = MockHttpRequest.get("/" + UserRootResource.USERS_PATH_V2 + "Neo");
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    assertTrue(response.getContentAsString().contains("\"name\":\"Neo\""));
    assertTrue(response.getContentAsString().contains("\"self\":{\"href\":\"/v2/users/Neo\"}"));
    assertTrue(response.getContentAsString().contains("\"delete\":{\"href\":\"/v2/users/Neo\"}"));
  }

  @Test
  public void shouldGet400OnCreatingNewUserWithNotAllowedCharacters() throws URISyntaxException {
    // the @ character at the begin of the name is not allowed
    String userJson = "{ \"name\": \"@user\",\"active\": true,\"admin\": false,\"displayName\": \"someone\",\"mail\": \"x@example.com\",\"type\": \"db\" }";
    MockHttpRequest request = MockHttpRequest
      .post("/" + UserRootResource.USERS_PATH_V2)
      .contentType(VndMediaType.USER)
      .content(userJson.getBytes());
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(400, response.getStatus());

    // the whitespace at the begin opf the name is not allowed
    userJson = "{ \"name\": \" user\",\"active\": true,\"admin\": false,\"displayName\": \"someone\",\"mail\": \"x@example.com\",\"type\": \"db\" }";
    request = MockHttpRequest
      .post("/" + UserRootResource.USERS_PATH_V2)
      .contentType(VndMediaType.USER)
      .content(userJson.getBytes());

    dispatcher.invoke(request, response);

    assertEquals(400, response.getStatus());
  }

  @Test
  @SubjectAware(username = "unpriv")
  public void shouldCreateLimitedResponseForSimpleUser() throws URISyntaxException, UnsupportedEncodingException {
    MockHttpRequest request = MockHttpRequest.get("/" + UserRootResource.USERS_PATH_V2 + "Neo");
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    assertTrue(response.getContentAsString().contains("\"name\":\"Neo\""));
    assertTrue(response.getContentAsString().contains("\"self\":{\"href\":\"/v2/users/Neo\"}"));
    assertFalse(response.getContentAsString().contains("\"delete\":{\"href\":\"/v2/users/Neo\"}"));
  }

  @Test
  public void shouldEncryptPasswordBeforeChanging() throws Exception {
    String newPassword = "pwd123";
    String content = String.format("{\"newPassword\": \"%s\"}", newPassword);
    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Neo/password")
      .contentType(VndMediaType.PASSWORD_OVERWRITE)
      .content(content.getBytes());
    MockHttpResponse response = new MockHttpResponse();
    when(passwordService.encryptPassword(newPassword)).thenReturn("encrypted123");

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
    verify(userManager).overwritePassword("Neo", "encrypted123");
  }

  @Test
  public void shouldGet400OnOverwritePasswordWhenManagerThrowsNotAllowed() throws Exception {
    originalUser.setType("not an xml type");
    String newPassword = "pwd123";
    String content = String.format("{\"newPassword\": \"%s\"}", newPassword);
    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Neo/password")
      .contentType(VndMediaType.PASSWORD_OVERWRITE)
      .content(content.getBytes());
    MockHttpResponse response = new MockHttpResponse();

    doThrow(new ChangePasswordNotAllowedException(ContextEntry.ContextBuilder.entity("passwordChange", "-"), "xml")).when(userManager).overwritePassword(any(), any());

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
  }

  @Test
  public void shouldGet404OnOverwritePasswordWhenNotFound() throws Exception {
    originalUser.setType("not an xml type");
    String newPassword = "pwd123";
    String content = String.format("{\"newPassword\": \"%s\"}", newPassword);
    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Neo/password")
      .contentType(VndMediaType.PASSWORD_OVERWRITE)
      .content(content.getBytes());
    MockHttpResponse response = new MockHttpResponse();

    doThrow(new NotFoundException("Test", "x")).when(userManager).overwritePassword(any(), any());

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }

  @Test
  public void shouldEncryptPasswordOnOverwritePassword() throws Exception {
    originalUser.setType("not an xml type");
    String newPassword = "pwd123";
    String content = String.format("{\"newPassword\": \"%s\"}", newPassword);
    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Neo/password")
      .contentType(VndMediaType.PASSWORD_OVERWRITE)
      .content(content.getBytes());
    MockHttpResponse response = new MockHttpResponse();
    when(passwordService.encryptPassword(newPassword)).thenReturn("encrypted123");

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
    verify(userManager).overwritePassword("Neo", "encrypted123");
  }

  @Test
  public void shouldEncryptPasswordBeforeCreatingUser() throws Exception {
    URL url = Resources.getResource("sonia/scm/api/v2/user-test-create.json");
    byte[] userJson = Resources.toByteArray(url);

    MockHttpRequest request = MockHttpRequest
      .post("/" + UserRootResource.USERS_PATH_V2)
      .contentType(VndMediaType.USER)
      .content(userJson);
    MockHttpResponse response = new MockHttpResponse();
    when(passwordService.encryptPassword("pwd123")).thenReturn("encrypted123");

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_CREATED, response.getStatus());
    verify(userManager).create(any(User.class));
    User createdUser = userCaptor.getValue();
    assertEquals("encrypted123", createdUser.getPassword());
  }

  @Test
  public void shouldIgnoreGivenPasswordOnUpdatingUser() throws Exception {
    URL url = Resources.getResource("sonia/scm/api/v2/user-test-update.json");
    byte[] userJson = Resources.toByteArray(url);

    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Neo")
      .contentType(VndMediaType.USER)
      .content(userJson);
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
    verify(userManager).modify(any(User.class));
    User updatedUser = userCaptor.getValue();
    assertEquals(originalUser.getPassword(), updatedUser.getPassword());
  }

  @Test
  public void shouldFailForMissingContent() throws URISyntaxException {
    MockHttpRequest request = MockHttpRequest
      .post("/" + UserRootResource.USERS_PATH_V2)
      .contentType(VndMediaType.USER)
      .content(new byte[]{});
    MockHttpResponse response = new MockHttpResponse();
    when(passwordService.encryptPassword("pwd123")).thenReturn("encrypted123");

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
  }

  @Test
  public void shouldGetNotFoundForNotExistentUser() throws URISyntaxException {
    MockHttpRequest request = MockHttpRequest.get("/" + UserRootResource.USERS_PATH_V2 + "nosuchuser");
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
  }

  @Test
  public void shouldDeleteUser() throws Exception {
    MockHttpRequest request = MockHttpRequest.delete("/" + UserRootResource.USERS_PATH_V2 + "Neo");
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    verify(userManager).delete(any(User.class));
    assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
  }

  @Test
  public void shouldFailUpdateForDifferentIds() throws Exception {
    URL url = Resources.getResource("sonia/scm/api/v2/user-test-update.json");
    byte[] userJson = Resources.toByteArray(url);
    createDummyUser("Other");

    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Other")
      .contentType(VndMediaType.USER)
      .content(userJson);
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    verify(userManager, never()).modify(any(User.class));
  }

  @Test
  public void shouldFailUpdateForUnknownEntity() throws Exception {
    URL url = Resources.getResource("sonia/scm/api/v2/user-test-update.json");
    byte[] userJson = Resources.toByteArray(url);
    when(userManager.get("Neo")).thenReturn(null);

    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Neo")
      .contentType(VndMediaType.USER)
      .content(userJson);
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    verify(userManager, never()).modify(any(User.class));
  }

  @Test
  public void shouldCreatePageForOnePageOnly() throws URISyntaxException, UnsupportedEncodingException {
    PageResult<User> singletonPageResult = createSingletonPageResult(1);
    when(userManager.getPage(any(), eq(0), eq(10))).thenReturn(singletonPageResult);
    MockHttpRequest request = MockHttpRequest.get("/" + UserRootResource.USERS_PATH_V2);
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    assertTrue(response.getContentAsString().contains("\"name\":\"Neo\""));
    assertTrue(response.getContentAsString().contains("\"self\":{\"href\":\"/v2/users/?page=0"));
    assertTrue(response.getContentAsString().contains("\"create\":{\"href\":\"/v2/users/\"}"));
    assertFalse(response.getContentAsString().contains("\"next\"")); // check for bug of edison-hal v2.0.0
  }

  @Test
  public void shouldCreatePageForMultiplePages() throws URISyntaxException, UnsupportedEncodingException {
    PageResult<User> singletonPageResult = createSingletonPageResult(3);
    when(userManager.getPage(any(), eq(1), eq(1))).thenReturn(singletonPageResult);
    MockHttpRequest request = MockHttpRequest.get("/" + UserRootResource.USERS_PATH_V2 + "?page=1&pageSize=1");
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    assertTrue(response.getContentAsString().contains("\"name\":\"Neo\""));
    assertTrue(response.getContentAsString().contains("\"self\":{\"href\":\"/v2/users/?page=1"));
    assertTrue(response.getContentAsString().contains("\"first\":{\"href\":\"/v2/users/?page=0"));
    assertTrue(response.getContentAsString().contains("\"prev\":{\"href\":\"/v2/users/?page=0"));
    assertTrue(response.getContentAsString().contains("\"next\":{\"href\":\"/v2/users/?page=2"));
    assertTrue(response.getContentAsString().contains("\"last\":{\"href\":\"/v2/users/?page=2"));
  }

  @Test
  public void shouldGetPermissionLink() throws URISyntaxException, UnsupportedEncodingException {
    MockHttpRequest request = MockHttpRequest.get("/" + UserRootResource.USERS_PATH_V2 + "Neo");
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_OK, response.getStatus());

    assertTrue(response.getContentAsString().contains("\"permissions\":{"));
  }

  @Test
  public void shouldGetPermissions() throws URISyntaxException, UnsupportedEncodingException {
    when(permissionAssigner.readPermissionsForUser("Neo")).thenReturn(singletonList(new PermissionDescriptor("something:*")));
    MockHttpRequest request = MockHttpRequest.get("/" + UserRootResource.USERS_PATH_V2 + "Neo/permissions");
    MockHttpResponse response = new MockHttpResponse();

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_OK, response.getStatus());

    assertTrue(response.getContentAsString().contains("\"permissions\":[\"something:*\"]"));
  }

  @Test
  public void shouldSetPermissions() throws URISyntaxException {
    MockHttpRequest request = MockHttpRequest
      .put("/" + UserRootResource.USERS_PATH_V2 + "Neo/permissions")
      .contentType(VndMediaType.PERMISSION_COLLECTION)
      .content("{\"permissions\":[\"other:*\"]}".getBytes());
    MockHttpResponse response = new MockHttpResponse();
    ArgumentCaptor<Collection<PermissionDescriptor>> captor = ArgumentCaptor.forClass(Collection.class);
    doNothing().when(permissionAssigner).setPermissionsForUser(eq("Neo"), captor.capture());

    dispatcher.invoke(request, response);

    assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());

    assertEquals("other:*", captor.getValue().iterator().next().getValue());
  }

  private PageResult<User> createSingletonPageResult(int overallCount) {
    return new PageResult<>(singletonList(createDummyUser("Neo")), overallCount);
  }

  private User createDummyUser(String name) {
    User user = new User();
    user.setName(name);
    user.setType("xml");
    user.setPassword("redpill");
    user.setCreationDate(System.currentTimeMillis());
    when(userManager.get(name)).thenReturn(user);
    return user;
  }
}
