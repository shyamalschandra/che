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

import org.eclipse.che.ide.websocket.MessageBus;

/**
 *
 */
public class WsAgent {


    private MessageBus messageBus;
    private String restApiEndPoint;
    private String webSocketEndPoint;

    public void setMessageBus(MessageBus messageBus) {
        this.messageBus = messageBus;
    }


    public void setRestApiEndPoint(String restApiEndPoint) {
        this.restApiEndPoint = restApiEndPoint;
    }

    public void setWebSocketEndPoint(String webSocketEndPoint) {
        this.webSocketEndPoint = webSocketEndPoint;
    }
}
