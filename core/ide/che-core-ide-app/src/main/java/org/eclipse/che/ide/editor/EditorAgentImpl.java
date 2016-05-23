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
package org.eclipse.che.ide.editor;

import com.google.common.base.Optional;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.ide.api.machine.events.WsAgentStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateHandler;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.ide.CoreLocalizationConstant;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorInput;
import org.eclipse.che.ide.api.editor.EditorOpenedEvent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.editor.EditorPartPresenter.EditorPartCloseHandler;
import org.eclipse.che.ide.api.editor.EditorProvider;
import org.eclipse.che.ide.api.editor.EditorRegistry;
import org.eclipse.che.ide.api.editor.OpenEditorCallbackImpl;
import org.eclipse.che.ide.api.event.ActivePartChangedEvent;
import org.eclipse.che.ide.api.event.ActivePartChangedHandler;
import org.eclipse.che.ide.api.event.FileContentUpdateEvent;
import org.eclipse.che.ide.api.event.FileEvent;
import org.eclipse.che.ide.api.event.FileEventHandler;
import org.eclipse.che.ide.api.event.WindowActionEvent;
import org.eclipse.che.ide.api.event.WindowActionHandler;
import org.eclipse.che.ide.api.filetypes.FileType;
import org.eclipse.che.ide.api.filetypes.FileTypeRegistry;
import org.eclipse.che.ide.api.parts.PartPresenter;
import org.eclipse.che.ide.api.parts.PropertyListener;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.api.resources.Container;
import org.eclipse.che.ide.api.resources.File;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.resources.ResourceChangedEvent;
import org.eclipse.che.ide.api.resources.ResourceChangedEvent.ResourceChangedHandler;
import org.eclipse.che.ide.api.resources.ResourceDelta;
import org.eclipse.che.ide.api.resources.VirtualFile;
import org.eclipse.che.ide.api.editor.texteditor.HasReadOnlyProperty;
import org.eclipse.che.ide.resource.Path;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.che.ide.api.parts.PartStackType.EDITING;
import static org.eclipse.che.ide.api.resources.Resource.FILE;
import static org.eclipse.che.ide.api.resources.ResourceDelta.ADDED;
import static org.eclipse.che.ide.api.resources.ResourceDelta.DERIVED;
import static org.eclipse.che.ide.api.resources.ResourceDelta.MOVED_FROM;
import static org.eclipse.che.ide.api.resources.ResourceDelta.MOVED_TO;
import static org.eclipse.che.ide.api.resources.ResourceDelta.REMOVED;
import static org.eclipse.che.ide.api.resources.ResourceDelta.UPDATED;

/**
 * Default implementation of {@link EditorAgent}.
 *
 * @author Evgen Vidolob
 * @author Vlad Zhukovskyi
 * @see EditorAgent
 **/
@Singleton
public class EditorAgentImpl implements EditorAgent,
                                        EditorPartCloseHandler,
                                        FileEventHandler,
                                        ActivePartChangedHandler,
                                        WindowActionHandler,
                                        ResourceChangedHandler,
                                        WsAgentStateHandler {

    private final EventBus                 eventBus;
    private final WorkspaceAgent           workspaceAgent;
    private final FileTypeRegistry         fileTypeRegistry;
    private final EditorRegistry           editorRegistry;
    private final CoreLocalizationConstant coreLocalizationConstant;

    private final List<EditorPartPresenter> openedEditors;
    private       List<EditorPartPresenter> dirtyEditors;
    private       EditorPartPresenter       activeEditor;

    @Inject
    public EditorAgentImpl(EventBus eventBus,
                           FileTypeRegistry fileTypeRegistry,
                           EditorRegistry editorRegistry,
                           WorkspaceAgent workspaceAgent,
                           CoreLocalizationConstant coreLocalizationConstant) {
        this.eventBus = eventBus;
        this.fileTypeRegistry = fileTypeRegistry;
        this.editorRegistry = editorRegistry;
        this.workspaceAgent = workspaceAgent;
        this.coreLocalizationConstant = coreLocalizationConstant;
        this.openedEditors = newArrayList();

        eventBus.addHandler(ActivePartChangedEvent.TYPE, this);
        eventBus.addHandler(FileEvent.TYPE, this);
        eventBus.addHandler(WindowActionEvent.TYPE, this);
        eventBus.addHandler(ResourceChangedEvent.getType(), this);
        eventBus.addHandler(WsAgentStateEvent.TYPE, this);
    }

    @Override
    public void onClose(EditorPartPresenter editor) {
        closeEditorPart(editor);
    }

    @Override
    public void onFileOperation(FileEvent event) {
        switch (event.getOperationType()) {
            case OPEN:
                openEditor(event.getFile());
                break;
            case CLOSE:
                closeEditor(event.getFile());
        }
    }

    @Override
    public void onActivePartChanged(ActivePartChangedEvent event) {
        if (event.getActivePart() instanceof EditorPartPresenter) {
            activeEditor = (EditorPartPresenter)event.getActivePart();
            activeEditor.activate();
        }
    }

    @Override
    public void onWindowClosing(WindowActionEvent event) {
        for (EditorPartPresenter editorPartPresenter : getOpenedEditors()) {
            if (editorPartPresenter.isDirty()) {
                event.setMessage(coreLocalizationConstant.changesMayBeLost()); //TODO need to move this into standalone component
                return;
            }
        }
    }

    @Override
    public void onWindowClosed(WindowActionEvent event) {
        //do nothing
    }

    @Override
    public void onResourceChanged(ResourceChangedEvent event) {
        final ResourceDelta delta = event.getDelta();
        final Resource resource = delta.getResource();

        switch (delta.getKind()) {
            case ADDED:
                onResourceCreated(delta);
                break;
            case REMOVED:
                onResourceRemoved(resource);
                break;
            case UPDATED:
                onResourceUpdated(delta);
        }
    }

    @Override
    public void onWsAgentStarted(WsAgentStateEvent event) {
        //do nothing
    }

    @Override
    public void onWsAgentStopped(WsAgentStateEvent event) {
        for (EditorPartPresenter editor : getOpenedEditors()) {
            closeEditorPart(editor);
        }
    }

    protected void onResourceCreated(ResourceDelta delta) {
        if ((delta.getFlags() & (MOVED_FROM | MOVED_TO)) == 0) {
            return;
        }

        final Resource resource = delta.getResource();
        final Path movedFrom = delta.getFromPath();

        if (resource.getResourceType() == FILE) {
            final EditorPartPresenter editor = getOpenedEditor(delta.getFromPath());
            if (editor != null) {
                editor.getEditorInput().setFile((File)resource);
                editor.onFileChanged();
                eventBus.fireEvent(new FileContentUpdateEvent(resource.getLocation().toString()));
            }
        } else {
            List<EditorPartPresenter> outdatedEditors = newArrayList();
            for (EditorPartPresenter editor : getOpenedEditors()) {
                if (movedFrom.isPrefixOf(editor.getEditorInput().getFile().getLocation())) {
                    outdatedEditors.add(editor);
                }
            }

            if (outdatedEditors.isEmpty()) {
                return;
            }

            for (final EditorPartPresenter editor : outdatedEditors) {
                final Path rawPath = editor.getEditorInput().getFile().getLocation().removeFirstSegments(movedFrom.segmentCount());

                ((Container)resource).getFile(rawPath).then(new Operation<Optional<File>>() {
                    @Override
                    public void apply(Optional<File> newFile) throws OperationException {
                        if (newFile.isPresent()) {
                            editor.getEditorInput().setFile(newFile.get());
                            editor.onFileChanged();
                            eventBus.fireEvent(new FileContentUpdateEvent(newFile.get().getLocation().toString()));
                        }
                    }
                });
            }
        }
    }

    protected void onResourceRemoved(Resource resource) {
        if (resource.getResourceType() == FILE) {
            closeEditorPart(getOpenedEditor(resource.getLocation()));
            return;
        }

        for (EditorPartPresenter editor : getOpenedEditors()) {
            final Path location = editor.getEditorInput().getFile().getLocation();
            if (resource.getLocation().equals(location) || resource.getLocation().isPrefixOf(location)) {
                closeEditorPart(editor);
            }
        }
    }

    protected void onResourceUpdated(ResourceDelta delta) {
        if ((delta.getFlags()) != DERIVED) {
            return;
        }

        final Resource resource = delta.getResource();

        if (resource.getResourceType() != FILE) {
            return;
        }

        final EditorPartPresenter editor = getOpenedEditor(resource.getLocation());

        if (editor != null) {
            editor.getEditorInput().setFile((File)resource);
            editor.onFileChanged();
            eventBus.fireEvent(new FileContentUpdateEvent(resource.getLocation().toString()));
        }
    }

    @Override
    public void openEditor(@NotNull final VirtualFile file) {
        doOpen(file, new OpenEditorCallbackImpl());
    }

    @Override
    public void closeEditor(VirtualFile file) {
        checkArgument(file != null, "Null file occurred");

        closeEditorPart(getOpenedEditor(file.getLocation()));
    }

    @Override
    public void openEditor(@NotNull VirtualFile file, @NotNull OpenEditorCallback callback) {
        doOpen(file, callback);
    }

    private void doOpen(final VirtualFile file, final OpenEditorCallback callback) {
        EditorPartPresenter openedEditor = getOpenedEditor(file.getLocation());
        if (openedEditor != null) {
            workspaceAgent.setActivePart(openedEditor, EDITING);
            callback.onEditorActivated(openedEditor);
        } else {
            FileType fileType = fileTypeRegistry.getFileTypeByFile(file);
            EditorProvider editorProvider = editorRegistry.getEditor(fileType);
            final EditorPartPresenter editor = editorProvider.getEditor();

            editor.init(new EditorInputImpl(fileType, file), callback);
            editor.addCloseHandler(this);

            workspaceAgent.openPart(editor, EDITING);
            openedEditors.add(editor);

            workspaceAgent.setActivePart(editor);
            editor.addPropertyListener(new PropertyListener() {
                @Override
                public void propertyChanged(PartPresenter source, int propId) {
                    if (propId == EditorPartPresenter.PROP_INPUT) {
                        if (editor instanceof HasReadOnlyProperty) {
                            ((HasReadOnlyProperty)editor).setReadOnly(file.isReadOnly());
                        }

                        callback.onEditorOpened(editor);
                        eventBus.fireEvent(new EditorOpenedEvent(file, editor));
                    }
                }
            });
        }
    }

    @Override
    public void activateEditor(@NotNull EditorPartPresenter editor) {
        workspaceAgent.setActivePart(editor);
    }

    @Override
    public List<EditorPartPresenter> getDirtyEditors() {
        List<EditorPartPresenter> dirtyEditors = new ArrayList<>();
        for (EditorPartPresenter partPresenter : openedEditors) {
            if (partPresenter.isDirty()) {
                dirtyEditors.add(partPresenter);
            }
        }
        return dirtyEditors;
    }

    protected void closeEditorPart(EditorPartPresenter editor) {
        if (editor == null) {
            return;
        }

        openedEditors.remove(editor);

        editor.close(false);

        if (activeEditor == null) {
            return;
        }

        final VirtualFile closedFile = editor.getEditorInput().getFile();
        final VirtualFile activeFile = activeEditor.getEditorInput().getFile();
        if (activeFile.equals(closedFile)) {
            activeEditor = null;
        }
    }

    @NotNull
    @Override
    public List<EditorPartPresenter> getOpenedEditors() {
        return newArrayList(openedEditors);
    }

    @Override
    public EditorPartPresenter getOpenedEditor(Path path) {
        for (EditorPartPresenter editor : openedEditors) {
            if (path.equals(editor.getEditorInput().getFile().getLocation())) {
                return editor;
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void saveAll(final AsyncCallback callback) {
        dirtyEditors = getDirtyEditors();
        if (dirtyEditors.isEmpty()) {
            callback.onSuccess("Success");
        } else {
            doSave(callback);
        }
    }

    private void doSave(final AsyncCallback callback) {
        final EditorPartPresenter partPresenter = dirtyEditors.get(0);
        partPresenter.doSave(new AsyncCallback<EditorInput>() {
            @Override
            public void onFailure(Throwable caught) {
                callback.onFailure(caught);
            }

            @Override
            public void onSuccess(EditorInput result) {
                dirtyEditors.remove(partPresenter);
                if (dirtyEditors.isEmpty()) {
                    callback.onSuccess("Success");
                } else {
                    doSave(callback);
                }
            }
        });
    }

    @Override
    public EditorPartPresenter getActiveEditor() {
        return activeEditor;
    }
}
