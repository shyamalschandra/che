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

import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.ResourceInterceptor;
import org.eclipse.che.ide.api.resources.marker.PresentableTextMarker;
import org.eclipse.che.plugin.maven.shared.MavenAttributes;

import java.util.List;
import java.util.Map;

import static org.eclipse.che.ide.api.resources.Resource.PROJECT;

/**
 * @author Vlad Zhukovskiy
 */
public class MavenProjectInterceptor implements ResourceInterceptor {

    /** {@inheritDoc} */
    @Override
    public Resource intercept(Resource resource) {
        if (resource.getResourceType() != PROJECT) {
            return resource;
        }

        final Map<String, List<String>> attributes = ((Project)resource).getAttributes();

        if (attributes != null && attributes.containsKey(MavenAttributes.ARTIFACT_ID)) {
            final String artifactId = attributes.get(MavenAttributes.ARTIFACT_ID).get(0);

            if (!artifactId.equals(resource.getName())) {
                resource.addMarker(new PresentableTextMarker("[" + artifactId + "]"));
            }
        }

        return resource;
    }

}
