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
package org.eclipse.che.api.machine.gwt.client;

import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.ide.websocket.MessageBus;

import javax.validation.constraints.NotNull;

/**
 *
 */
public class DevMachine {

    private final MachineDto devMachineDescriptor;

    public DevMachine(@NotNull MachineDto devMachineDescriptor) {
        this.devMachineDescriptor = devMachineDescriptor;

    }

    public String getType() {
        devMachineDescriptor.getConfig().getType();
    }





}
