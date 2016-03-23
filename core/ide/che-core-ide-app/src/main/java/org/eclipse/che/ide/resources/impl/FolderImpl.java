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
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.promises.client.PromiseProvider;
import org.eclipse.che.ide.api.resources.Folder;
import org.eclipse.che.ide.resource.Path;

/**
 * Default implementation of the {@code Folder}.
 *
 * @author Vlad Zhukovskyi
 * @see ContainerImpl
 * @see Folder
 * @since 4.0.0-RC14
 */
@Beta
class FolderImpl extends ContainerImpl implements Folder {

    @Inject
    protected FolderImpl(@Assisted Path path,
                         @Assisted ResourceManager resourceManager,
                         PromiseProvider promiseProvider) {
        super(path, resourceManager, promiseProvider);
    }

    /** {@inheritDoc} */
    @Override
    public final int getResourceType() {
        return FOLDER;
    }
}