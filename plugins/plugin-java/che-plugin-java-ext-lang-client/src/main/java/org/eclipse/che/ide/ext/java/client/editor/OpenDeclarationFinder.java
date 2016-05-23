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
package org.eclipse.che.ide.ext.java.client.editor;

import com.google.common.base.Optional;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseProvider;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.event.FileEvent;
import org.eclipse.che.ide.api.resources.Container;
import org.eclipse.che.ide.api.resources.File;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.SyntheticFile;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.api.workspace.Workspace;
import org.eclipse.che.ide.ext.java.client.navigation.service.JavaNavigationService;
import org.eclipse.che.ide.ext.java.client.resource.SourceFolderMarker;
import org.eclipse.che.ide.ext.java.client.util.JavaUtil;
import org.eclipse.che.ide.ext.java.shared.JarEntry;
import org.eclipse.che.ide.ext.java.shared.OpenDeclarationDescriptor;
import org.eclipse.che.ide.api.editor.text.LinearRange;
import org.eclipse.che.ide.api.editor.texteditor.TextEditorPresenter;
import org.eclipse.che.ide.ext.java.shared.dto.ClassContent;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.util.loging.Log;

import static org.eclipse.che.ide.api.event.FileEvent.FileOperation.OPEN;

/**
 * @author Evgen Vidolob
 * @author Vlad Zhukovskyi
 */
@Singleton
public class OpenDeclarationFinder {

    private final EditorAgent           editorAgent;
    private final JavaNavigationService navigationService;
    private final Workspace             workspace;
    private final EventBus              eventBus;
    private final PromiseProvider       promises;

    @Inject
    public OpenDeclarationFinder(EditorAgent editorAgent,
                                 JavaNavigationService navigationService,
                                 Workspace workspace,
                                 EventBus eventBus,
                                 PromiseProvider promises) {
        this.editorAgent = editorAgent;
        this.navigationService = navigationService;
        this.workspace = workspace;
        this.eventBus = eventBus;
        this.promises = promises;
    }

    public void openDeclaration() {
        EditorPartPresenter activeEditor = editorAgent.getActiveEditor();
        if (activeEditor == null) {
            return;
        }

        if (!(activeEditor instanceof TextEditorPresenter)) {
            Log.error(getClass(), "Open Declaration support only TextEditorPresenter as editor");
            return;
        }
        TextEditorPresenter editor = ((TextEditorPresenter)activeEditor);
        int offset = editor.getCursorOffset();
        final VirtualFile file = editor.getEditorInput().getFile();

        if (file instanceof Resource) {
            final Optional<Project> project = ((Resource)file).getRelatedProject();

            final Optional<Resource> srcFolder = ((Resource)file).getParentWithMarker(SourceFolderMarker.ID);

            if (!srcFolder.isPresent()) {
                return;
            }

            final String fqn = JavaUtil.resolveFQN((Container)srcFolder.get(), (Resource)file);

            navigationService.findDeclaration(project.get().getLocation(), fqn, offset).then(new Operation<OpenDeclarationDescriptor>() {
                @Override
                public void apply(OpenDeclarationDescriptor result) throws OperationException {
                    if (result != null) {
                        handleDescriptor(project.get(), result);
                    }
                }
            });

        }
    }

    private void handleDescriptor(final Project project, final OpenDeclarationDescriptor descriptor) {
        EditorPartPresenter openedEditor = editorAgent.getOpenedEditor(Path.valueOf(descriptor.getPath()));
        if (openedEditor != null) {
            editorAgent.activateEditor(openedEditor);
            fileOpened(openedEditor, descriptor.getOffset());
            return;
        }

        if (descriptor.isBinary()) {
            navigationService.getEntry(project.getLocation(), descriptor.getLibId(), descriptor.getPath())
                             .then(new Operation<JarEntry>() {
                                 @Override
                                 public void apply(final JarEntry entry) throws OperationException {
                                     navigationService
                                             .getContent(project.getLocation(), descriptor.getLibId(), Path.valueOf(entry.getPath()))
                                             .then(new Operation<ClassContent>() {
                                                 @Override
                                                 public void apply(ClassContent content) throws OperationException {
                                                     VirtualFile file = new SyntheticFile(entry.getName(), content.getContent(), promises);
                                                     eventBus.fireEvent(new FileEvent(file, OPEN));
                                                 }
                                             });
                                 }
                             });
        } else {
            workspace.getWorkspaceRoot().getFile(Path.valueOf(descriptor.getPath())).then(new Operation<Optional<File>>() {
                @Override
                public void apply(Optional<File> file) throws OperationException {
                    if (file.isPresent()) {
                        eventBus.fireEvent(new FileEvent(file.get(), OPEN));
                    }
                }
            });
        }
    }

    private void fileOpened(final EditorPartPresenter editor, final int offset) {
        new Timer() { //in some reason we need here timeout otherwise it not work cursor don't set to correct position
            @Override
            public void run() {
                if (editor instanceof TextEditorPresenter) {
                    ((TextEditorPresenter)editor).getDocument().setSelectedRange(
                            LinearRange.createWithStart(offset).andLength(0), true);
                }
            }
        }.schedule(100);
    }
}
