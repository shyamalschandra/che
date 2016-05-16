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
package org.eclipse.che.ide.ext.git.client.commit;

import org.eclipse.che.api.git.shared.Revision;
import org.eclipse.che.ide.ext.git.client.BaseTest;
import org.eclipse.che.ide.ext.git.client.DateTimeFormatter;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testing {@link CommitPresenter} functionality.
 *
 * @author Andrey Plotnikov
 * @author Vlad Zhukovskyi
 */
public class CommitPresenterTest extends BaseTest {
    @Captor
    private ArgumentCaptor<AsyncRequestCallback<Revision>> asyncRequestCallbackRevisionCaptor;

    public static final boolean ALL_FILE_INCLUDES = true;
    public static final boolean IS_OVERWRITTEN    = true;
    public static final String  COMMIT_TEXT       = "commit text";
    @Mock
    private CommitView               view;
    @Mock
    private Revision                 revision;
    @Mock
    private DateTimeFormatter        dateTimeFormatter;

    private CommitPresenter presenter;

    @Override
    public void disarm() {
        super.disarm();

        presenter = new CommitPresenter(view,
                                        service,
                                        constant,
                                        notificationManager,
                                        dialogFactory,
                                        appContext,
                                        dateTimeFormatter,
                                        gitOutputConsoleFactory,
                                        consolesPanelPresenter,
                                        workspace);
    }

    @Test
    public void testShowDialog() throws Exception {
        when(view.getMessage()).thenReturn(EMPTY_TEXT);
        presenter.showDialog(project);

        verify(view).setAmend(eq(!IS_OVERWRITTEN));
        verify(view).setAllFilesInclude(eq(!ALL_FILE_INCLUDES));
        verify(view).focusInMessageField();
        verify(view).setEnableCommitButton(eq(DISABLE_BUTTON));
        verify(view).getMessage();
        verify(view).showDialog();
    }

    @Test
    public void testShowDialogWithExistingMessage() throws Exception {
        when(view.getMessage()).thenReturn("foo");
        presenter.showDialog(project);

        verify(view).setAmend(eq(!IS_OVERWRITTEN));
        verify(view).setAllFilesInclude(eq(!ALL_FILE_INCLUDES));
        verify(view).focusInMessageField();
        verify(view).setEnableCommitButton(eq(ENABLE_BUTTON));
        verify(view).getMessage();
        verify(view).showDialog();
    }

    @Test
    public void testOnCommitClickedWhenCommitWSRequestIsSuccessful() throws Exception {
//        when(view.getMessage()).thenReturn(COMMIT_TEXT);
//        when(view.isAllFilesInclued()).thenReturn(ALL_FILE_INCLUDES);
//        when(view.isAmend()).thenReturn(IS_OVERWRITTEN);
//
//        when(service.commit(anyObject(), any(Path.class), anyString(), anyBoolean(), anyBoolean())).thenReturn(revisionPromise);
//        when(revisionPromise.then(any(Operation.class))).thenReturn(revisionPromise);
//        when(revisionPromise.catchError(any(Operation.class))).thenReturn(revisionPromise);
//
//        presenter.showDialog(project);
//        presenter.onCommitClicked();
//
//        verify(view, times(2)).getMessage();
//        verify(view).isAllFilesInclued();
//        verify(view).isAmend();
//
//        verify(revisionPromise).then(revisionCaptor.capture());
//        revisionCaptor.getValue().apply(revision);
//        verify(view).close();
//        verify(view).setMessage(eq(EMPTY_TEXT));
//        verify(gitOutputConsoleFactory).create(COMMIT_COMMAND_NAME);
//        verify(console).print(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString());
    }

    @Test
    public void testOnCommitClickedWhenCommitRequestIsFailed() throws Exception {
//        when(view.getMessage()).thenReturn(COMMIT_TEXT);
//        when(view.isAllFilesInclued()).thenReturn(ALL_FILE_INCLUDES);
//        when(view.isAmend()).thenReturn(IS_OVERWRITTEN);
//
//        when(service.commit(anyObject(), any(Path.class), anyString(), anyBoolean(), anyBoolean())).thenReturn(revisionPromise);
//        when(revisionPromise.then(any(Operation.class))).thenReturn(revisionPromise);
//        when(revisionPromise.catchError(any(Operation.class))).thenReturn(revisionPromise);
//
//        presenter.showDialog(project);
//        presenter.onCommitClicked();
//
//        verify(view, times(2)).getMessage();
//        verify(view).isAllFilesInclued();
//        verify(view).isAmend();
//
//        verify(revisionPromise).catchError(promiseErrorCaptor.capture());
//        promiseErrorCaptor.getValue().apply(promiseError);
//
//        verify(view).close();
//        verify(view, times(0)).setMessage(anyString());
//        verify(constant).commitFailed();
//        verify(gitOutputConsoleFactory).create(COMMIT_COMMAND_NAME);
//        verify(console).printError(anyString());
//        verify(consolesPanelPresenter).addCommandOutput(anyString(), eq(console));
//        verify(notificationManager).notify(anyString(), anyString(), any(StatusNotification.Status.class), anyObject());
    }

    @Test
    public void testOnCancelClicked() throws Exception {
        presenter.onCancelClicked();

        verify(view).close();
    }

    @Test
    public void testOnValueChangedWhenCommitMessageEmpty() throws Exception {
        when(view.getMessage()).thenReturn(EMPTY_TEXT);

        presenter.onValueChanged();

        verify(view).setEnableCommitButton(eq(DISABLE_BUTTON));
    }

    @Test
    public void testOnValueChanged() throws Exception {
        when(view.getMessage()).thenReturn(COMMIT_TEXT);

        presenter.onValueChanged();

        verify(view).setEnableCommitButton(eq(!DISABLE_BUTTON));
    }
}
