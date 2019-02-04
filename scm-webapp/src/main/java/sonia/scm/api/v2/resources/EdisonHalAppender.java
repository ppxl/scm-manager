package sonia.scm.api.v2.resources;

import de.otto.edison.hal.Link;
import de.otto.edison.hal.Links;

import java.util.ArrayList;
import java.util.List;

class EdisonHalAppender implements HalAppender {

  private final Links.Builder builder;

  EdisonHalAppender(Links.Builder builder) {
    this.builder = builder;
  }

  @Override
  public void appendLink(String rel, String href) {
    builder.single(Link.link(rel, href));
  }

  @Override
  public LinkArrayBuilder linkArrayBuilder(String rel) {
    return new EdisonLinkArrayBuilder(builder, rel);
  }

  private static class EdisonLinkArrayBuilder implements LinkArrayBuilder {

    private final Links.Builder builder;
    private final String rel;
    private final List<Link> linkArray = new ArrayList<>();

    private EdisonLinkArrayBuilder(Links.Builder builder, String rel) {
      this.builder = builder;
      this.rel = rel;
    }

    @Override
    public LinkArrayBuilder append(String name, String href) {
      linkArray.add(Link.linkBuilder(rel, href).withName(name).build());
      return this;
    }

    @Override
    public void build() {
      builder.array(linkArray);
    }
  }
}
