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
package org.eclipse.che.plugin.maven.client.resource;

import com.google.inject.Singleton;

import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.ResourceInterceptor;
import org.eclipse.che.ide.api.resources.marker.PresentableTextMarker;
import org.eclipse.che.plugin.maven.shared.MavenAttributes;

import java.util.List;
import java.util.Map;

import static org.eclipse.che.ide.api.resources.Resource.FILE;

/**
 * Intercept java based files (.java), cut extension and adds the marker which is responsible for displaying presentable text
 * to the corresponding resource.
 *
 * @author Vlad Zhukovskiy
 * @since 4.1.0-RC1
 */
@Singleton
public class PomInterceptor implements ResourceInterceptor {

    /** {@inheritDoc} */
    @Override
    public Resource intercept(Resource resource) {
        if (resource.getResourceType() != FILE) {
            return resource;
        }

        if (!MavenAttributes.POM_XML.equals(resource.getName())) {
            return resource;
        }

        final Project project = resource.getRelatedProject();

        if (!project.getType().equals(MavenAttributes.MAVEN_ID)) {
            return resource;
        }

        final Map<String, List<String>> attributes = project.getAttributes();

        final String displayName;

        if (attributes != null && attributes.containsKey(MavenAttributes.ARTIFACT_ID)) {
            displayName = attributes.get(MavenAttributes.ARTIFACT_ID).get(0);
        } else {
            displayName = project.getName() + "/" + resource.getName();
        }

        resource.addMarker(new PresentableTextMarker(displayName));

        return resource;
    }
}
