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

import com.google.common.base.Strings;
import com.google.gwt.user.client.Window;

import org.eclipse.che.api.core.model.machine.Server;
import org.eclipse.che.api.machine.shared.Constants;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.ServerDto;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class DevMachine {

    private final MachineDto devMachineDescriptor;

    private final Map<String, DevMachineServer> servers;


    public DevMachine(@NotNull MachineDto devMachineDescriptor) {
        this.devMachineDescriptor = devMachineDescriptor;
        Map<String, ServerDto> dtoMap = devMachineDescriptor.getRuntime().getServers();
        servers = new HashMap<>(dtoMap.size());
        for (String s : dtoMap.keySet()) {
            servers.put(s, new DevMachineServer(dtoMap.get(s)));
        }
    }

    public String getType() {
        return devMachineDescriptor.getConfig().getType();
    }

    public String getWsAgentWebSocketUrl() {
        DevMachineServer server = getServer(Constants.WSAGENT_REFERENCE);
        if (server != null) {
            String url = server.getUrl();
            String extUrl = url.substring(url.indexOf(':'), url.length());
            boolean isSecureConnection = Window.Location.getProtocol().equals("https:");
            return (isSecureConnection ? "wss" : "ws") + extUrl + "/ws/" + getWorkspace();
        } else {
            return  null; //should not be
        }
    }

    public String getWsAgentBaseUrl() {
        DevMachineServer server = getServer(Constants.WSAGENT_REFERENCE);
        if (server != null) {
            return server.getUrl();
        } else {
            return  null; //should not be
        }
    }

    public Map<String, DevMachineServer> getServers() {
        return servers;
    }


    public DevMachineServer getServer(String reference) {
        if (!Strings.isNullOrEmpty(reference)) {
            for (DevMachineServer server : servers.values()) {
                if (reference.equals(server.getRef())) {
                    return server;
                }
            }
        }
        return null;
    }

    public String getWorkspace(){
        return devMachineDescriptor.getWorkspaceId();
    }

    public String getId() {
        return devMachineDescriptor.getId();
    }


    public class DevMachineServer implements Server {

        private final String path;
        private final String address;
        private final String protocol;
        private final String ref;
        private final String url;

        public DevMachineServer(ServerDto dto) {
            path = dto.getPath();
            address = dto.getAddress();
            protocol = dto.getProtocol();
            ref = dto.getRef();
            url = dto.getUrl();
        }


        @Override
        public String getRef() {
            return ref;
        }

        @Override
        public String getAddress() {
            return address;
        }

        @Override
        public String getProtocol() {
            return protocol;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getUrl() {
            return url;
        }
    }


}
