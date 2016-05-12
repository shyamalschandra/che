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
package org.eclipse.che.ide.ext.git.client.url;

import org.eclipse.che.api.git.shared.Remote;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.ext.git.client.BaseTest;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link ShowProjectGitReadOnlyUrlPresenter} functionality.
 *
 * @author Andrey Plotnikov
 * @author Oleksii Orel
 * @author Vlad Zhukovskyi
 */
public class ShowProjectGitReadOnlyUrlPresenterTest extends BaseTest {

    @Captor
    private ArgumentCaptor<AsyncRequestCallback<String>> asyncRequestCallbackGitReadOnlyUrlCaptor;

    @Captor
    private ArgumentCaptor<AsyncRequestCallback<List<Remote>>> asyncRequestCallbackRemoteListCaptor;

    @Mock
    private ShowProjectGitReadOnlyUrlView      view;
    private ShowProjectGitReadOnlyUrlPresenter presenter;

    @Override
    public void disarm() {
        super.disarm();

        presenter = new ShowProjectGitReadOnlyUrlPresenter(view,
                                                           service,
                                                           appContext,
                                                           constant,
                                                           notificationManager,
                                                           gitOutputConsoleFactory,
                                                           consolesPanelPresenter,
                                                           workspace);

        when(service.getGitReadOnlyUrl(anyObject(), any(Path.class))).thenReturn(stringPromise);
        when(stringPromise.then(any(Operation.class))).thenReturn(stringPromise);
        when(stringPromise.catchError(any(Operation.class))).thenReturn(stringPromise);

        when(service.remoteList(anyObject(), any(Path.class), anyString(), anyBoolean())).thenReturn(remoteListPromise);
        when(remoteListPromise.then(any(Operation.class))).thenReturn(remoteListPromise);
        when(remoteListPromise.catchError(any(Operation.class))).thenReturn(remoteListPromise);
    }

    @Test
    public void getGitReadOnlyUrlAsyncCallbackIsSuccess() throws Exception {
        presenter.showDialog(project);

        verify(stringPromise).then(stringCaptor.capture());
        stringCaptor.getValue().apply(LOCALE_URI);

        verify(view).setLocaleUrl(eq(LOCALE_URI));
    }

    @Test
    public void getGitReadOnlyUrlAsyncCallbackIsFailed() throws Exception {
//        presenter.showDialog(project);
//
//        verify(stringPromise).catchError(promiseErrorCaptor.capture());
//        promiseErrorCaptor.getValue().apply(promiseError);
//
//        verify(console).printError(anyString());
//        verify(constant).initFailed();
    }

    @Test
    public void getGitRemoteListAsyncCallbackIsSuccess() throws Exception {
        final List<Remote> remotes = new ArrayList<>();
        remotes.add(mock(Remote.class));

        presenter.showDialog(project);

        verify(remoteListPromise).then(remoteListCaptor.capture());
        remoteListCaptor.getValue().apply(remotes);

        verify(view).setRemotes(anyObject());
    }

    @Test
    public void getGitRemoteListAsyncCallbackIsFailed() throws Exception {
//        presenter.showDialog(project);
//
//        verify(remoteListPromise).catchError(promiseErrorCaptor.capture());
//        promiseErrorCaptor.getValue().apply(promiseError);
//
//        verify(view).setRemotes(null);
//        verify(console).printError(anyString());
//        verify(notificationManager).notify(anyString(), any(StatusNotification.Status.class), anyObject());
    }

    @Test
    public void testOnCloseClicked() throws Exception {
        presenter.onCloseClicked();

        verify(view).close();
    }
}
