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
package org.eclipse.che.ide.api.app;

import com.google.common.annotations.Beta;

import org.eclipse.che.api.factory.shared.dto.Factory;
import org.eclipse.che.ide.api.machine.DevMachine;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.api.workspace.Workspace;
import org.eclipse.che.api.workspace.shared.dto.WorkspaceDto;

import java.util.List;

/**
 * Represents current context of the IDE application.
 *
 * @author Vitaly Parfonov
 * @author Artem Zatsarynnyi
 * @author Vlad Zhukovskyi
 */
public interface AppContext {

    /** Returns list of start-up actions with parameters that comes form URL during IDE initialization. */
    List<StartUpAction> getStartAppActions();

    /** @deprecated use {@link Workspace} */
    @Deprecated
    WorkspaceDto getWorkspace();

    /** @deprecated use {@link Workspace} */
    @Deprecated
    void setWorkspace(WorkspaceDto workspace);

    /**
     * Returns id of current workspace of throws IllegalArgumentException if workspace is null.
     *
     * @deprecated use {@link Workspace#getId()}
     **/
    @Deprecated
    String getWorkspaceId();

    /**
     * Returns {@link CurrentProject} instance that describes the project
     * that is currently opened or <code>null</code> if none opened.
     * <p/>
     * Note that current project may also represent a project's module.
     *
     * @return opened project or <code>null</code> if none opened
     */
    @Deprecated
    CurrentProject getCurrentProject();

    /**
     * Returns current user.
     *
     * @return current user
     */
    CurrentUser getCurrentUser();

    void setCurrentUser(CurrentUser currentUser);

    /**
     * Returns list of projects paths which are in importing state.
     *
     * @return list of project paths
     */
    List<String> getImportingProjects();

    /**
     * Adds project path to list of projects which are in importing state.
     *
     * @param pathToProject
     *         project path
     */
    void addProjectToImporting(String pathToProject);

    /**
     * Removes project path to list of projects which are in importing state.
     *
     * @param pathToProject
     *         project path
     */
    void removeProjectFromImporting(String pathToProject);

    /**
     * List of action with params that comes from startup URL.
     * Can be processed after IDE initialization as usual after
     * starting ws-agent.
     */
    void setStartUpActions(List<StartUpAction> startUpActions);

    /**
     * Returns {@link Factory} instance which id was set on startup,
     * or {@code null} if no factory was specified.
     *
     * @return loaded factory or {@code null}
     */
    Factory getFactory();

    void setFactory(Factory factory);

    /** Returns instance  of the developer machine (where workspace is bound). */
    DevMachine getDevMachine();

    void setDevMachine(DevMachine devMachine);

    String getProjectsRoot();

    void setProjectsRoot(String projectsRoot);


    /**
     * Returns the resource which is in current context. By current context means, that resource may be
     * in use in specified part if IDE. For example, project part may provide resource which is under
     * selection at this moment, editor may provide resource which is open, full text search may provide
     * resource which is under selection.
     * <p/>
     * If specified part provides more than one resource, then last selected resource is returned.
     *
     * May return {@code null} if there is no resource in context.
     *
     * @return the resource in context
     * @see Resource
     * @see #getResources()
     * @since 4.0.0-RC14
     */
    @Beta
    Resource getResource();

    /**
     * Returns the resources which are in current context. By current context means, that resources may be
     * in use in specified part if IDE. For example, project part may provide resources which are under
     * selection at this moment, editor may provide resource which is open, full text search may provide
     * resources which are under selection.
     * <p/>
     * If specified part provides more than one resource, then all selected resources are returned.
     *
     * May return {@code null} if there is no resources in context.
     *
     * @return the resource in context
     * @see Resource
     * @see #getResource()
     * @since 4.0.0-RC14
     */
    @Beta
    Resource[] getResources();

    /**
     * Returns the root project which is in context. To find out specified sub-project in context, method
     * {@link #getResource()} should be called. Resource is bound to own project and to get {@link Project}
     * instance from {@link Resource}, method {@link Resource#getRelatedProject()} should be called.
     *
     * May return {@code null} if there is no project in context.
     *
     * @return the root project or {@code null}
     * @see Project
     * @since 4.0.0-RC14
     */
    @Beta
    Project getRootProject();
}
