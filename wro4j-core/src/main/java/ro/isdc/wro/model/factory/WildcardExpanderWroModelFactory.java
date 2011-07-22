/**
 * Copyright@2011 wro4j
 */
package ro.isdc.wro.model.factory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.group.Inject;
import ro.isdc.wro.model.resource.DuplicateResourceDetector;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.locator.ResourceLocator;
import ro.isdc.wro.model.resource.locator.factory.ResourceLocatorFactory;
import ro.isdc.wro.model.resource.locator.support.AbstractResourceLocator;
import ro.isdc.wro.model.resource.locator.wildcard.WildcardExpandedHandlerAware;
import ro.isdc.wro.model.resource.locator.wildcard.WildcardStreamLocator;
import ro.isdc.wro.util.Transformer;


/**
 * A decorator which looks up for resources containing wildcards and replaces them with the corresponding collection of
 * specific resources. For example, a resource of this type:
 * <p/>
 * {@code
 *   <js>/path/to/**.js</js>
 * }
 * <p/>
 * can be transformed into:
 * <p/>
 * {@code
 *   <js>/path/to/a1.js</js>
 * } <br/>
 * {@code
 *   <js>/path/to/a2.js</js>
 * } <br/>
 * {@code
 *   <js>/path/to/a3.js</js>
 * } <br/>
 * <p/>
 * This model transformation is also known as wildcard expander, because it mutates the model after it is built by
 * adding resources to the group which contains resources with wildcard uri.
 *
 * @author Alex Objelean
 * @created 18 Jul 2011
 * @since 1.3.9
 */
public class WildcardExpanderWroModelFactory extends WroModelFactoryDecorator {
  private static final Logger LOG = LoggerFactory.getLogger(WildcardExpanderWroModelFactory.class);

  @Inject
  private ResourceLocatorFactory resourceLocatorFactory;
  @Inject
  private DuplicateResourceDetector duplicateResourceDetector;

  public WildcardExpanderWroModelFactory(final WroModelFactory decorated) {
    super(decorated);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public WroModel create() {
    final WroModel model = super.create();

    for (final Group group : model.getGroups()) {
      for (final Resource resource : group.getResources()) {
        LOG.debug("resource uri: {}", resource.getUri());
        final ResourceLocator resourceLocator = resourceLocatorFactory.locate(resource.getUri());
        LOG.debug("uriLocator: " + resourceLocator);
        if (resourceLocator instanceof AbstractResourceLocator) {
          final WildcardStreamLocator wildcardStreamLocator = ((AbstractResourceLocator)resourceLocator).getWildcardStreamLocator();
          if (wildcardStreamLocator.hasWildcard(resource.getUri()) && wildcardStreamLocator instanceof WildcardExpandedHandlerAware) {
            LOG.debug("Wildcard Expander for uri: " + resource.getUri());

            // force the reset of the detector to avoid situations when resources are considered duplicates in unit
            // tests.
            duplicateResourceDetector.reset();

            //create the handler which expand the resources containing wildcard.
            final Transformer<Collection<File>> wildcardExpanderHandler = new Transformer<Collection<File>>() {
              public Collection<File> transform(final Collection<File> files) {
                final List<Resource> expandedResources = new ArrayList<Resource>();
                for (final File file : files) {
                  final String resourceUri = "file:" + file.getAbsolutePath().replace('\\', '/');
                  expandedResources.add(Resource.create(resourceUri, resource.getType()));
                }
                LOG.debug("replace resource {}", resource);
                group.replace(resource, expandedResources);
                //Because there is actually no transformation, here it doesn't matter what we return.
                return null;
              }
            };

            ((WildcardExpandedHandlerAware)wildcardStreamLocator).setWildcardExpanderHandler(wildcardExpanderHandler);
            try {
              // trigger the wildcard replacement
              resourceLocator.getInputStream();
            } catch (final IOException e) {
              // log only
              LOG.error("problem while trying to expande wildcard for the following resource uri: " + resource.getUri());
            } finally {
              //remove the handler, it is not needed anymore
              ((WildcardExpandedHandlerAware)wildcardStreamLocator).setWildcardExpanderHandler(null);
            }

          }
        }
      }
    }
    return model;
  }
}
