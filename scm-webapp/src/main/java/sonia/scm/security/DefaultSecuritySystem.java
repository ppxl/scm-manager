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

package sonia.scm.security;


import com.github.legman.Subscribe;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sonia.scm.HandlerEventType;
import sonia.scm.auditlog.AuditEntry;
import sonia.scm.auditlog.Auditor;
import sonia.scm.auditlog.EntryCreationContext;
import sonia.scm.event.ScmEventBus;
import sonia.scm.group.GroupEvent;
import sonia.scm.plugin.PluginLoader;
import sonia.scm.store.ConfigurationEntryStore;
import sonia.scm.store.ConfigurationEntryStoreFactory;
import sonia.scm.user.UserEvent;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;


/**
 * @author Sebastian Sdorra
 * @since 1.31
 */
@Singleton
public class DefaultSecuritySystem implements SecuritySystem {

  private static final String NAME = "security";

  private static final String PERMISSION_DESCRIPTOR =
    "META-INF/scm/permissions.xml";

  private static final Logger logger =
    LoggerFactory.getLogger(DefaultSecuritySystem.class);

  private final ConfigurationEntryStore<AssignedPermission> store;

  private final ImmutableSet<PermissionDescriptor> availablePermissions;

  private final Set<Auditor> auditors;

  @Inject
  public DefaultSecuritySystem(ConfigurationEntryStoreFactory storeFactory, PluginLoader pluginLoader, Set<Auditor> auditors) {
    store = storeFactory
      .withType(AssignedPermission.class)
      .withName(NAME)
      .build();
    this.availablePermissions = readAvailablePermissions(pluginLoader);
    this.auditors = auditors;
  }

  @Override
  public void addPermission(AssignedPermission permission) {
    assertHasPermission();
    validatePermission(permission);

    callAuditors(null, permission);

    store.put(permission);

    //J-
    ScmEventBus.getInstance().post(
      new AssignedPermissionEvent(HandlerEventType.CREATE, permission)
    );
    //J+
  }

  @Override
  public void deletePermission(AssignedPermission permission) {
    assertHasPermission();
    boolean deleted = deletePermissions(sap -> Objects.equal(sap.getName(), permission.getName())
      && Objects.equal(sap.isGroupPermission(), permission.isGroupPermission())
      && Objects.equal(sap.getPermission(), permission.getPermission()));
    if (deleted) {
      callAuditors(permission, null);
      ScmEventBus.getInstance().post(
        new AssignedPermissionEvent(HandlerEventType.DELETE, permission)
      );
    }
  }

  @Subscribe
  public void handleEvent(final UserEvent event) {
    if (event.getEventType() == HandlerEventType.DELETE) {
      deletePermissions(p -> !p.isGroupPermission()
        && event.getItem().getName().equals(p.getName()));
    }
  }

  @Subscribe
  public void handleEvent(final GroupEvent event) {
    if (event.getEventType() == HandlerEventType.DELETE) {
      deletePermissions(p -> p.isGroupPermission()
        && event.getItem().getName().equals(p.getName()));
    }
  }

  @Override
  public Collection<PermissionDescriptor> getAvailablePermissions() {
    assertHasPermission();

    return availablePermissions;
  }

  @Override
  public Collection<AssignedPermission> getPermissions(Predicate<AssignedPermission> predicate) {
    Builder<AssignedPermission> permissions = ImmutableSet.builder();

    for (Entry<String, AssignedPermission> e : store.getAll().entrySet()) {
      if ((predicate == null) || predicate.test(e.getValue())) {
        permissions.add(new StoredAssignedPermission(e.getKey(), e.getValue()));
      }
    }

    return permissions.build();
  }

  private void assertHasPermission() {
    PermissionPermissions.assign().check();
  }

  private boolean deletePermissions(Predicate<AssignedPermission> predicate) {
    List<Entry<String, AssignedPermission>> toRemove =
      store.getAll()
        .entrySet()
        .stream()
        .filter(e -> (predicate == null) || predicate.test(e.getValue())).collect(Collectors.toList());
    toRemove.forEach(e -> store.remove(e.getKey()));
    return !toRemove.isEmpty();
  }

  private static List<PermissionDescriptor> parsePermissionDescriptor(
    JAXBContext context, URL descriptorUrl) {
    List<PermissionDescriptor> descriptors = emptyList();

    try {
      PermissionDescriptors descriptorWrapper =
        (PermissionDescriptors) context.createUnmarshaller().unmarshal(
          descriptorUrl);

      descriptors = descriptorWrapper.getPermissions();

      logger.debug("found {} permissions at {}", descriptors.size(),
        descriptorUrl);
      logger.trace("permissions from {}: {}", descriptorUrl, descriptors);
    } catch (JAXBException ex) {
      logger.error("could not parse permission descriptor", ex);
    }

    return descriptors;
  }

  private static ImmutableSet<PermissionDescriptor> readAvailablePermissions(PluginLoader pluginLoader) {
    ImmutableSet.Builder<PermissionDescriptor> builder = ImmutableSet.builder();

    try {
      JAXBContext context =
        JAXBContext.newInstance(PermissionDescriptors.class);

      // Querying permissions from uberClassLoader returns also the permissions from plugin
      Enumeration<URL> descirptorEnum =
        pluginLoader.getUberClassLoader().getResources(PERMISSION_DESCRIPTOR);

      while (descirptorEnum.hasMoreElements()) {
        URL descriptorUrl = descirptorEnum.nextElement();

        logger.debug("read permission descriptor from {}", descriptorUrl);

        builder.addAll(parsePermissionDescriptor(context, descriptorUrl));
      }
    } catch (IOException ex) {
      logger.error("could not read permission descriptors", ex);
    } catch (JAXBException ex) {
      logger.error(
        "could not create jaxb context to read permission descriptors", ex);
    }

    return builder.build();
  }

  private void validatePermission(AssignedPermission perm) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(perm.getName()),
      "name is required");
    Preconditions.checkArgument(!isNull(perm.getPermission()),
      "permission is required");
  }

  private void callAuditors(AssignedPermission notModified, AssignedPermission newObject) {
    AssignedPermission nonNullPermission = newObject == null ? notModified : newObject;
    if (nonNullPermission.getClass().isAnnotationPresent(AuditEntry.class)) {
      String label = nonNullPermission.isGroupPermission() ? "group" : "user";
      EntryCreationContext<AssignedPermission> context = new EntryCreationContext<>(newObject, notModified, nonNullPermission.getEntityName(), Set.of(label));
      auditors.forEach(s -> s.createEntry(context));
    }
  }

  /**
   * Descriptor for permissions.
   */
  @XmlRootElement(name = "permissions")
  @XmlAccessorType(XmlAccessType.FIELD)
  private static class PermissionDescriptors {

    public List<PermissionDescriptor> getPermissions() {
      if (permissions == null) {
        permissions = emptyList();
      }

      return permissions;
    }

    @XmlElement(name = "permission")
    private List<PermissionDescriptor> permissions;
  }
}
