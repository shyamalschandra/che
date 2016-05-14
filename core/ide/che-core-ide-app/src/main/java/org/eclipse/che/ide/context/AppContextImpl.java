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
package org.eclipse.che.ide.context;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.factory.shared.dto.Factory;
import org.eclipse.che.ide.api.machine.DevMachine;
import org.eclipse.che.ide.api.machine.events.WsAgentStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateHandler;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.app.CurrentProject;
import org.eclipse.che.ide.api.app.CurrentUser;
import org.eclipse.che.ide.api.app.StartUpAction;
import org.eclipse.che.ide.api.data.HasDataObject;
import org.eclipse.che.ide.api.event.SelectionChangedEvent;
import org.eclipse.che.ide.api.event.SelectionChangedHandler;
import org.eclipse.che.ide.api.event.project.CurrentProjectChangedEvent;
import org.eclipse.che.ide.api.event.project.ProjectUpdatedEvent;
import org.eclipse.che.ide.api.event.project.ProjectUpdatedEvent.ProjectUpdatedHandler;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.selection.Selection;
import org.eclipse.che.ide.api.workspace.Workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link AppContext}.
 *
 * @author Vitaly Parfonov
 * @author Artem Zatsarynnyi
 * @author Vlad Zhukovskyi
 */
@Singleton
public class AppContextImpl implements AppContext, SelectionChangedHandler, WsAgentStateHandler, ProjectUpdatedHandler {

    private final EventBus                  eventBus;
    private final BrowserQueryFieldRenderer browserQueryFieldRenderer;
    private final Workspace workspace;
    private final List<String>              projectsInImport;

    private WorkspaceDto   usersWorkspaceDto;
    private CurrentProject      currentProject;
    private CurrentUser         currentUser;
    private Factory             factory;
    private DevMachine          devMachine;
    private String              projectsRoot;
    /**
     * List of actions with parameters which comes from startup URL.
     * Can be processed after IDE initialization as usual after starting ws-agent.
     */
    private List<StartUpAction> startAppActions;

    private Resource   currentResource;
    private Resource[] currentResources;

    @Inject
    public AppContextImpl(EventBus eventBus, BrowserQueryFieldRenderer browserQueryFieldRenderer,
                          Workspace workspace) {
        this.eventBus = eventBus;
        this.browserQueryFieldRenderer = browserQueryFieldRenderer;
        this.workspace = workspace;

        projectsInImport = new ArrayList<>();

        eventBus.addHandler(SelectionChangedEvent.TYPE, this);
        eventBus.addHandler(WsAgentStateEvent.TYPE, this);
        eventBus.addHandler(ProjectUpdatedEvent.getType(), this);
    }

    @Override
    public WorkspaceDto getWorkspace() {
        return usersWorkspaceDto;
    }

    @Override
    public void setWorkspace(WorkspaceDto workspace) {
        this.usersWorkspaceDto = workspace;
    }

    @Override
    public String getWorkspaceId() {
        if (usersWorkspaceDto == null) {
            throw new IllegalArgumentException(getClass() + " Workspace can not be null.");
        }

        return usersWorkspaceDto.getId();
    }

    @Override
    public CurrentProject getCurrentProject() {
        return currentProject;
    }

    @Override
    public CurrentUser getCurrentUser() {
        return currentUser;
    }

    @Override
    public void setCurrentUser(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public List<String> getImportingProjects() {
        return projectsInImport;
    }

    @Override
    public void addProjectToImporting(String pathToProject) {
        projectsInImport.add(pathToProject);
    }

    @Override
    public void removeProjectFromImporting(String pathToProject) {
        projectsInImport.remove(pathToProject);
    }

    @Override
    public List<StartUpAction> getStartAppActions() {
        return startAppActions;
    }

    @Override
    public void setStartUpActions(List<StartUpAction> startUpActions) {
        this.startAppActions = startUpActions;
    }

    @Override
    public Factory getFactory() {
        return factory;
    }

    @Override
    public void setFactory(Factory factory) {
        this.factory = factory;
    }

    @Override
    public DevMachine getDevMachine() {
        return devMachine;
    }

    @Override
    public void setDevMachine(DevMachine devMachine) {
        this.devMachine = devMachine;
    }

    @Override
    public String getProjectsRoot() {
        return projectsRoot;
    }

    @Override
    public void setProjectsRoot(String projectsRoot) {
        this.projectsRoot = projectsRoot;
    }

    @Override
    public void onSelectionChanged(SelectionChangedEvent event) {
        final Selection<?> selection = event.getSelection();
        if (selection instanceof Selection.NoSelectionProvided) {
            return;
        }

        browserQueryFieldRenderer.setProjectName("");

        currentResource = null;
        currentResources = null;

        if (selection == null || selection.getHeadElement() == null) {
            return;
        }

        final Object headObject = selection.getHeadElement();
        final List<?> allObjects = selection.getAllElements();

        if (headObject instanceof HasDataObject) {
            Object data = ((HasDataObject)headObject).getData();

            if (data instanceof Resource) {
                currentResource = (Resource)data;
            }
        } else if (headObject instanceof Resource) {
            currentResource = (Resource)headObject;
        }

        Set<Resource> resources = Sets.newHashSet();

        for (Object object : allObjects) {
            if (object instanceof HasDataObject) {
                Object data = ((HasDataObject)object).getData();

                if (data instanceof Resource) {
                    resources.add((Resource)data);
                }
            } else if (object instanceof Resource) {
                resources.add((Resource)object);
            }
        }

        currentResources = resources.toArray(new Resource[resources.size()]);
    }

    @Override
    public void onWsAgentStarted(WsAgentStateEvent event) {
    }

    @Override
    public void onWsAgentStopped(WsAgentStateEvent event) {
        currentProject = null;
        browserQueryFieldRenderer.setProjectName("");
    }

    @Override
    public void onProjectUpdated(ProjectUpdatedEvent event) {
        final ProjectConfigDto updatedProjectDescriptor = event.getUpdatedProjectDescriptor();
        final String updatedProjectDescriptorPath = updatedProjectDescriptor.getPath();

        if (updatedProjectDescriptorPath.equals(currentProject.getProjectConfig().getPath())) {
            currentProject.setProjectConfig(updatedProjectDescriptor);
            eventBus.fireEvent(new CurrentProjectChangedEvent(updatedProjectDescriptor));
        }

        if (updatedProjectDescriptorPath.equals(currentProject.getRootProject().getPath())) {
            currentProject.setRootProject(updatedProjectDescriptor);
            browserQueryFieldRenderer.setProjectName(updatedProjectDescriptor.getName());
        }
    }

    @Override
    public Resource getResource() {
        return currentResource;
    }

    @Override
    public Resource[] getResources() {
        return currentResources;
    }

    @Override
    public Project getRootProject() {
        if (currentResource == null) {
            return null;
        }

        if (currentResources == null) {
            return null;
        }

        Project root = null;

        for (Project project : workspace.getProjects()) {
            if (project.getLocation().isPrefixOf(currentResources[0].getLocation())) {
                root = project;
            }
        }

        if (root == null) {
            return null;
        }

        for (int i = 1; i < currentResources.length; i++) {
            if (!root.getLocation().isPrefixOf(currentResources[i].getLocation())) {
                return null;
            }
        }

        return root;
    }
}
