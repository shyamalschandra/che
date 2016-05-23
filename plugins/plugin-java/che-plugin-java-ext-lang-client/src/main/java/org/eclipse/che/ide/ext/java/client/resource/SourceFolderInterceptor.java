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
package org.eclipse.che.ide.ext.java.client.resource;

import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.ResourceInterceptor;
import org.eclipse.che.ide.ext.java.shared.Constants;
import org.eclipse.che.ide.ext.java.shared.ContentRoot;
import org.eclipse.che.ide.resource.Path;

import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static org.eclipse.che.ide.api.resources.Resource.FOLDER;
import static org.eclipse.che.ide.ext.java.client.util.JavaUtil.isJavaProject;
import static org.eclipse.che.ide.ext.java.shared.ContentRoot.SOURCE;

/**
 * @author Vlad Zhukovskiy
 */
public class SourceFolderInterceptor implements ResourceInterceptor {

    @Override
    public final Resource intercept(Resource resource) {
        checkArgument(resource != null, "Null resource occurred");

        if (resource.getResourceType() != FOLDER) {
            return resource;
        }

        final Project project = resource.getRelatedProject().get();

        if (project != null && isJavaProject(project)) {
            final Path resourcePath = resource.getLocation();

            for (Path path : getPaths(project, getAttribute())) {
                if (path.equals(resourcePath)) {
                    resource.addMarker(new SourceFolderMarker(getContentRoot()));
                    return resource;
                }
            }
        }

        return resource;
    }

    protected ContentRoot getContentRoot() {
        return SOURCE;
    }

    protected String getAttribute() {
        return Constants.SOURCE_FOLDER;
    }

    protected final Path[] getPaths(Project project, String srcType) {
        final List<String> srcFolders = project.getAttributes().get(srcType);

        if (srcFolders == null || srcFolders.isEmpty()) {
            return new Path[0];
        }

        Path[] paths = new Path[0];

        for (String srcFolder : srcFolders) {
            final int index = paths.length;
            paths = Arrays.copyOf(paths, index + 1);
            paths[index] = project.getLocation().append(srcFolder);
        }

        return paths;
    }
}
