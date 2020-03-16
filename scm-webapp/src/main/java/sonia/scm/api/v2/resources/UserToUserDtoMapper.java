package sonia.scm.api.v2.resources;

import de.otto.edison.hal.Embedded;
import de.otto.edison.hal.Links;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import sonia.scm.security.PermissionPermissions;
import sonia.scm.user.User;
import sonia.scm.user.UserManager;
import sonia.scm.user.UserPermissions;

import javax.inject.Inject;

import static de.otto.edison.hal.Embedded.embeddedBuilder;
import static de.otto.edison.hal.Link.link;
import static de.otto.edison.hal.Links.linkingTo;

// Mapstruct does not support parameterized (i.e. non-default) constructors. Thus, we need to use field injection.
@SuppressWarnings("squid:S3306")
@Mapper
public abstract class UserToUserDtoMapper extends BaseMapper<User, UserDto> {

  @Inject
  private UserManager userManager;

  @Override
  @Mapping(target = "attributes", ignore = true)
  @Mapping(target = "password", ignore = true)
  public abstract UserDto map(User modelObject);

  @Inject
  private ResourceLinks resourceLinks;

  @ObjectFactory
  UserDto createDto(User user) {
    Links.Builder linksBuilder = linkingTo().self(resourceLinks.user().self(user.getName()));
    if (UserPermissions.delete(user).isPermitted()) {
      linksBuilder.single(link("delete", resourceLinks.user().delete(user.getName())));
    }
    if (UserPermissions.modify(user).isPermitted()) {
      linksBuilder.single(link("update", resourceLinks.user().update(user.getName())));
      if (userManager.isTypeDefault(user)) {
        linksBuilder.single(link("password", resourceLinks.user().passwordChange(user.getName())));
      }
    }
    if (PermissionPermissions.read().isPermitted()) {
      linksBuilder.single(link("permissions", resourceLinks.userPermissions().permissions(user.getName())));
    }

    Embedded.Builder embeddedBuilder = embeddedBuilder();
    applyEnrichers(new EdisonHalAppender(linksBuilder, embeddedBuilder), user);

    return new UserDto(linksBuilder.build(), embeddedBuilder.build());
  }

}