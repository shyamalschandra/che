/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.resources.impl;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import org.eclipse.che.ide.api.resources.Container;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.resource.Path;

import java.util.Comparator;
import java.util.Map;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.of;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.System.arraycopy;
import static java.util.Arrays.binarySearch;
import static java.util.Arrays.copyOf;

/**
 * In memory implementation of {@link ResourceStore}.
 *
 * @author Vlad Zhukovskiy
 * @see ResourceStore
 * @since 4.0.0-RC14
 */
@Beta
class InMemoryResourceStore implements ResourceStore {

    Map<Path, Resource[]> memoryCache;

    private static final Resource[] EMPTY_RESOURCES = new Resource[0];

    private static final Comparator<Resource> NAME_COMPARATOR = new Comparator<Resource>() {
        @Override
        public int compare(Resource o1, Resource o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    public InMemoryResourceStore() {
        memoryCache = Maps.newHashMap();
    }

    /** {@inheritDoc} */
    @Override
    public boolean register(Path parent, Resource resource) {
        checkArgument(parent != null, "Null parent occurred");
        checkArgument(resource != null, "Null resource occurred");

        if (!memoryCache.containsKey(parent)) {
            memoryCache.put(parent, new Resource[]{resource});

            return true;
        } else {
            Resource[] container = memoryCache.get(parent);

            final int index = binarySearch(container, resource, NAME_COMPARATOR);

            if (index >= 0) { //update existing resource with new one
                container[index] = resource;

                return false;
            } else { //such resource doesn't exists, then simply add it
                final int posIndex = -index - 1; //negate inverted index into positive one
                final int size = container.length;
                final Resource[] tmpContainer = copyOf(container, size + 1);
                arraycopy(tmpContainer, posIndex, tmpContainer, posIndex + 1, size - posIndex); //prepare cell to insert
                tmpContainer[posIndex] = resource;
                container = tmpContainer;

                memoryCache.put(parent, container);

                return true;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void dispose(Path path, boolean withChildren) {
        checkArgument(path != null, "Null path occurred");

        if (!memoryCache.containsKey(path)) {
            return;
        }

        final Resource[] container = memoryCache.remove(path);

        if (withChildren) {
            for (Resource resource : container) {
                if (resource instanceof Container) {
                    dispose(resource.getLocation(), true);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Resource> getResource(Path path) {
        checkArgument(path != null, "Null path occurred");

        final Path parent = path.parent();

        if (!memoryCache.containsKey(parent)) {
            return absent();
        }

        final Resource[] container = memoryCache.get(parent);

        if (container == null) {
            return absent();
        }

        for (Resource resource : container) {
            if (resource.getLocation().equals(path)) {
                return Optional.of(resource);
            }
        }

        return absent();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Resource[]> get(Path parent) {
        checkArgument(parent != null, "Null path occurred");

        if (!memoryCache.containsKey(parent)) {
            return absent();
        }

        return of(memoryCache.get(parent));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Resource[]> getAll(Path parent) {
        checkArgument(parent != null, "Null path occurred");

        if (!memoryCache.containsKey(parent)) {
            return absent();
        }

        Resource[] all = new Resource[0];

        for (Map.Entry<Path, Resource[]> setEntry : memoryCache.entrySet()) {

            /* There is no need to check compared path if its segment count is less then given one. */

            final Path comparedPath = setEntry.getKey();

            if (parent.segmentCount() > comparedPath.segmentCount() && !parent.isPrefixOf(comparedPath)) {
                continue;
            }

            final Resource[] resources = setEntry.getValue();

            if (resources == null || resources.length == 0) {
                continue;
            }

            final Resource[] tmpResourcesArr = copyOf(all, all.length + resources.length);
            arraycopy(resources, 0, tmpResourcesArr, all.length, resources.length);
            all = tmpResourcesArr;
        }

        if (all.length == 0) {
            return of(EMPTY_RESOURCES);
        }

        return of(all);
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        memoryCache.clear();
    }
}
