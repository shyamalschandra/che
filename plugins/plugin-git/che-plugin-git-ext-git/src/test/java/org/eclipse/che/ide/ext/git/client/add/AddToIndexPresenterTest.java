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
package org.eclipse.che.ide.ext.git.client.add;

import org.eclipse.che.api.git.shared.Status;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.ext.git.client.BaseTest;
import org.eclipse.che.ide.resource.Path;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;

import java.util.List;

import static org.eclipse.che.ide.ext.git.client.add.AddToIndexPresenter.ADD_TO_INDEX_COMMAND_NAME;
import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.FLOAT_MODE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link AddToIndexPresenter} functionality.
 *
 * @author Andrey Plotnikov
 * @author Vlad Zhukovskyi
 */
public class AddToIndexPresenterTest extends BaseTest {
    public static final boolean  NEED_UPDATING = true;
    public static final String   MESSAGE       = "message";

    @Mock
    private AddToIndexView           view;
    @Mock
    private Status                   statusResponse;

    private AddToIndexPresenter presenter;

    @Override
    public void disarm() {
        super.disarm();

        presenter = new AddToIndexPresenter(view,
                                            appContext,
                                            constant,
                                            gitOutputConsoleFactory,
                                            consolesPanelPresenter,
                                            service,
                                            notificationManager,
                                            workspace);
    }

    @Test
    public void testDialogWillNotBeShownWhenStatusRequestIsFailed() throws Exception {
//        when(service.getStatus(anyObject(), any(Path.class))).thenReturn(statusPromise);
//        when(statusPromise.then(any(Operation.class))).thenReturn(statusPromise);
//        when(statusPromise.catchError(any(Operation.class))).thenReturn(statusPromise);
//
//        presenter.showDialog(project);
//
//        verify(statusPromise).catchError(promiseErrorCaptor.capture());
//        promiseErrorCaptor.getValue().apply(null);
//
//        verify(gitOutputConsoleFactory).create(ADD_TO_INDEX_COMMAND_NAME);
//        verify(console).printError(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString(), anyObject(), eq(FLOAT_MODE));
//        verify(view, never()).showDialog();
//        verify(constant, times(2)).statusFailed();
    }

    @Test
    public void testDialogWillNotBeShownWhenNothingAddToIndex() throws Exception {
//        when(statusResponse.isClean()).thenReturn(true);
//        when(service.getStatus(anyObject(), any(Path.class))).thenReturn(statusPromise);
//        when(statusPromise.then(any(Operation.class))).thenReturn(statusPromise);
//        when(statusPromise.catchError(any(Operation.class))).thenReturn(statusPromise);
//
//        presenter.showDialog(project);
//
//        verify(statusPromise).then(statusPromiseCaptor.capture());
//        statusPromiseCaptor.getValue().apply(statusResponse);
//
//        verify(gitOutputConsoleFactory).create(ADD_TO_INDEX_COMMAND_NAME);
//        verify(console).print(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString());
//        verify(view, never()).showDialog();
//        verify(constant, times(2)).nothingAddToIndex();
    }

    @Test
    public void testShowDialogWhenRootFolderIsSelected() throws Exception {
        when(service.getStatus(anyObject(), any(Path.class))).thenReturn(statusPromise);
        when(statusPromise.then(any(Operation.class))).thenReturn(statusPromise);
        when(statusPromise.catchError(any(Operation.class))).thenReturn(statusPromise);
        when(appContext.getResources()).thenReturn(new Resource[]{mock(Resource.class)});
        when(constant.addToIndexAllChanges()).thenReturn(MESSAGE);

        presenter.showDialog(project);

        verify(statusPromise).then(statusPromiseCaptor.capture());
        statusPromiseCaptor.getValue().apply(statusResponse);

        verify(constant).addToIndexAllChanges();
        verify(view).setMessage(eq(MESSAGE), Matchers.<List<String>>eq(null));
        verify(view).setUpdated(anyBoolean());
        verify(view).showDialog();
    }

    @Test
    public void testOnAddClickedWhenAddWSRequestIsSuccessful() throws Exception {
//        reset(gitOutputConsoleFactory);
//        when(service.add(anyObject(), any(Path.class), anyBoolean(), any(Path[].class))).thenReturn(voidPromise);
//        when(voidPromise.then(any(Operation.class))).thenReturn(voidPromise);
//        when(voidPromise.catchError(any(Operation.class))).thenReturn(voidPromise);
//        when(gitOutputConsoleFactory.create(anyString())).thenReturn(console);
//        when(view.isUpdated()).thenReturn(NEED_UPDATING);
//        when(constant.addSuccess()).thenReturn(MESSAGE);
//        when(service.getStatus(anyObject(), any(Path.class))).thenReturn(statusPromise);
//        when(statusPromise.then(any(Operation.class))).thenReturn(statusPromise);
//        when(statusPromise.catchError(any(Operation.class))).thenReturn(statusPromise);
//
//        when(appContext.getResources()).thenReturn(new Resource[]{file_1, file_2});
//
//        presenter.showDialog(project);
//        presenter.onAddClicked();
//
//        verify(voidPromise).then(voidPromiseCaptor.capture());
//        voidPromiseCaptor.getValue().apply(null);
//
//        verify(view).isUpdated();
//        verify(view).close();
//        verify(console).print(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString());
//        verify(constant, times(2)).addSuccess();
    }

    @Test
    public void testOnAddClickedWhenAddWSRequestIsFailed() throws Exception {
//        reset(gitOutputConsoleFactory);
//        when(gitOutputConsoleFactory.create(anyString())).thenReturn(console);
//        when(view.isUpdated()).thenReturn(NEED_UPDATING);
//        when(service.add(anyObject(), any(Path.class), anyBoolean(), any(Path[].class))).thenReturn(voidPromise);
//        when(voidPromise.then(any(Operation.class))).thenReturn(voidPromise);
//        when(voidPromise.catchError(any(Operation.class))).thenReturn(voidPromise);
//        when(service.getStatus(anyObject(), any(Path.class))).thenReturn(statusPromise);
//        when(statusPromise.then(any(Operation.class))).thenReturn(statusPromise);
//        when(statusPromise.catchError(any(Operation.class))).thenReturn(statusPromise);
//        when(appContext.getResources()).thenReturn(new Resource[]{file_1, file_2});
//
//        presenter.showDialog(project);
//        presenter.onAddClicked();
//
//        verify(voidPromise).catchError(promiseErrorCaptor.capture());
//        promiseErrorCaptor.getValue().apply(null);
//
//        verify(view).isUpdated();
//        verify(view).close();
//        verify(gitOutputConsoleFactory, times(2)).create(ADD_TO_INDEX_COMMAND_NAME);
//        verify(console).printError(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString(), anyObject(), eq(FLOAT_MODE));
//        verify(constant, times(2)).addFailed();
    }

    @Test
    public void testOnCancelClicked() throws Exception {
        presenter.onCancelClicked();

        verify(view).close();
    }
}
