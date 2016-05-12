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

import com.google.inject.Singleton;

import org.eclipse.che.ide.api.resources.File;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.ResourceInterceptor;
import org.eclipse.che.ide.api.resources.marker.PresentableTextMarker;

import static org.eclipse.che.ide.api.resources.Resource.FILE;
import static org.eclipse.che.ide.ext.java.client.util.JavaUtil.isJavaFile;

/**
 * Intercept java based files (.java), cut extension and adds the marker which is responsible for displaying presentable text
 * to the corresponding resource.
 *
 * @author Vlad Zhukovskiy
 * @since 4.1.0-RC1
 */
@Singleton
public class ClassInterceptor implements ResourceInterceptor {

    /** {@inheritDoc} */
    @Override
    public Resource intercept(Resource resource) {
        if (resource.getResourceType() != FILE) {
            return resource;
        }

        if (isJavaFile(resource)) {
            final String name = resource.getName();
            final String extension = ((File)resource).getFileExtension();

            resource.addMarker(new PresentableTextMarker(name.substring(0, name.length() - extension.length() - 1)));
        }

        return resource;
    }
}
