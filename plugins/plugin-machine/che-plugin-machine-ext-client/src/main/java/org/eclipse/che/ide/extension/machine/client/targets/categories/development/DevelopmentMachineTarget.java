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
package org.eclipse.che.ide.extension.machine.client.targets.categories.development;

import org.eclipse.che.ide.extension.machine.client.targets.BaseTarget;
import org.eclipse.che.ide.extension.machine.client.targets.Target;

import java.util.Objects;

/**
 * The implementation of {@link Target}.
 *
 * @author Oleksii Orel
 */
public class DevelopmentMachineTarget  extends BaseTarget {
    private String type;
    private String owner;
    private String sourceType;
    private String sourceUrl;
    private String sourceContent;


    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceContent(String sourceContent) {
        this.sourceContent = sourceContent;
    }

    public String getSourceContent() {
        return sourceContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof DevelopmentMachineTarget)) {
            return false;
        }

        DevelopmentMachineTarget other = (DevelopmentMachineTarget)o;

        return Objects.equals(getName(), other.getName())
               && Objects.equals(getCategory(), other.getCategory())
               && Objects.equals(getRecipe(), other.getRecipe())
               && Objects.equals(getType(), other.getType())
               && Objects.equals(getOwner(), other.getOwner())
               && Objects.equals(getSourceType(), other.getSourceType())
               && Objects.equals(getSourceContent(), other.getSourceContent())
               && Objects.equals(getSourceUrl(), other.getSourceUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), getCategory(), getRecipe(), getType(), getOwner(), getSourceType(), getSourceUrl(), getSourceContent());
    }
}
