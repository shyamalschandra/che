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
package org.eclipse.che.ide.ext.git.client.delete;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.ext.git.client.BaseTest;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.ui.window.Window;
import org.junit.Test;
import org.mockito.Mock;

import static org.eclipse.che.ide.ext.git.client.delete.DeleteRepositoryPresenter.DELETE_REPO_COMMAND_NAME;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link DeleteRepositoryPresenter} functionality.
 *
 * @author Andrey Plotnikov
 * @author Vlad Zhukovskyi
 */
public class DeleteRepositoryPresenterTest extends BaseTest {
    private DeleteRepositoryPresenter presenter;

    @Mock
    Window.Resources resources;

    @Mock
    Window.Css css;

    @Override
    public void disarm() {
        super.disarm();
        when(resources.windowCss()).thenReturn(css);
        when(css.alignBtn()).thenReturn("sdgsdf");
        when(css.glassVisible()).thenReturn("sdgsdf");
        when(css.contentVisible()).thenReturn("sdgsdf");
        when(css.animationDuration()).thenReturn(1);
        presenter = new DeleteRepositoryPresenter(service,
                                                  constant,
                                                  gitOutputConsoleFactory,
                                                  consolesPanelPresenter,
                                                  appContext,
                                                  notificationManager,
                                                  workspace);
    }

    @Test
    public void testDeleteRepositoryWhenDeleteRepositoryIsSuccessful() throws Exception {

//        when(service.deleteRepository(anyObject(), any(Path.class))).thenReturn(voidPromise);
//        when(voidPromise.then(any(Operation.class))).thenReturn(voidPromise);
//        when(voidPromise.catchError(any(Operation.class))).thenReturn(voidPromise);
//
//        presenter.deleteRepository(project);
//
//        verify(voidPromise).then(voidPromiseCaptor.capture());
//        voidPromiseCaptor.getValue().apply(null);
//
//        verify(gitOutputConsoleFactory).create(DELETE_REPO_COMMAND_NAME);
//        verify(console).print(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString());
    }

    @Test
    public void testDeleteRepositoryWhenDeleteRepositoryIsFailed() throws Exception {

//        when(service.deleteRepository(anyObject(), any(Path.class))).thenReturn(voidPromise);
//        when(voidPromise.then(any(Operation.class))).thenReturn(voidPromise);
//        when(voidPromise.catchError(any(Operation.class))).thenReturn(voidPromise);
//
//        presenter.deleteRepository(project);
//
//        verify(voidPromise).catchError(promiseErrorCaptor.capture());
//        promiseErrorCaptor.getValue().apply(promiseError);
//
//        verify(gitOutputConsoleFactory).create(DELETE_REPO_COMMAND_NAME);
//        verify(console).printError(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString(), any(StatusNotification.Status.class), anyObject());
    }
}
