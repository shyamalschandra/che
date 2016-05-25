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
package org.eclipse.che.ide.extension.machine.client.actions;

import com.google.gwtmockito.GwtMockitoTestRunner;
import org.eclipse.che.ide.CoreLocalizationConstant;
import org.eclipse.che.ide.actions.WorkspaceSnapshotCreator;
import org.eclipse.che.ide.api.action.ActionEvent;
import org.eclipse.che.ide.api.action.Presentation;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.machine.DevMachine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CreateSnapshotAction}.
 *
 * @author Yevhenii Voevodin
 */
@RunWith(GwtMockitoTestRunner.class)
public class CreateSnapshotActionTest {

    @Mock
    private WorkspaceSnapshotCreator snapshotCreator;

    @Mock
    private CoreLocalizationConstant coreLocalizationConstant;

    @Mock
    private AppContext appContext;

    @Mock(answer = Answers.RETURNS_MOCKS)
    private ActionEvent event;

    @InjectMocks
    private CreateSnapshotAction createSnapshotAction;

    @Test
    public void shouldSetTitleAndDescription() {
        verify(coreLocalizationConstant).createSnapshotTitle();
        verify(coreLocalizationConstant).createSnapshotDescription();
    }

    @Test
    public void eventPresentationShouldBeEnabledIfSnapshotCreatorIsNotInProgress() {
        when(event.getPresentation()).thenReturn(new Presentation());
        when(snapshotCreator.isInProgress()).thenReturn(false);

        createSnapshotAction.updateInPerspective(event);

        assertTrue(event.getPresentation().isEnabled());
    }

    @Test
    public void eventPresentationShouldBeDisabledIfSnapshotCreatorIsInProgress() {
        when(event.getPresentation()).thenReturn(new Presentation());
        when(snapshotCreator.isInProgress()).thenReturn(true);

        createSnapshotAction.updateInPerspective(event);

        assertFalse(event.getPresentation().isEnabled());
    }

    @Test
    public void shouldCreateSnapshotWithWorkspaceIdFromAppContextWhenActionPerformed() {
        DevMachine devMachine = mock(DevMachine.class);
        when(devMachine.getId()).thenReturn("workspace123");
        when(appContext.getDevMachine()).thenReturn(devMachine);

        createSnapshotAction.actionPerformed(event);

        verify(snapshotCreator).createSnapshot("workspace123");
    }


}
