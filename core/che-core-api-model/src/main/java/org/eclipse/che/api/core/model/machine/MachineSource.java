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
package org.eclipse.che.api.core.model.machine;

/**
 * @author gazarenkov
 */
public interface MachineSource {

    /**
     * Returns dockerfile, image, ssh-config, etc
     */
    String getType();

    /**
     * Returns URL or ID
     */
    String getLocation();

    /**
     * @return content of the machine source. No need to use an external link.
     */
    String getContent();

    /**
     * Defines the new content to use for this machine source.
     * Alternate way is to provide a location
     * @param content the content instead of an external link like with location
     */
    void setContent(String content);

    /**
     * Defines the new content to use for this machine source.
     * Alternate way is to provide a location
     * @param content the content instead of an external link like with location
     * @return the current intance of the object
     */
    MachineSource withContent(String content);

}
