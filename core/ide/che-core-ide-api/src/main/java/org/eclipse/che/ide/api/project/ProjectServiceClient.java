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
package org.eclipse.che.ide.api.project;

import org.eclipse.che.ide.api.machine.DevMachine;
import org.eclipse.che.api.project.shared.dto.ItemReference;
import org.eclipse.che.api.project.shared.dto.SourceEstimation;
import org.eclipse.che.api.project.shared.dto.TreeElement;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.rest.AsyncRequestCallback;
import org.eclipse.che.ide.websocket.rest.RequestCallback;

import java.util.List;

/**
 * Serves the connections with the server side project service.
 * <p/>
 * By design this service is laid on the lowest business level which is operating only with data transfer objects.
 * This interface is not intended to implementing by the third party components or using it directly.
 *
 * @author Vitaly Parfonov
 * @author Artem Zatsarynnyi
 * @author Vlad Zhukovskyi
 */
public interface ProjectServiceClient {
    Promise<List<ProjectConfigDto>> getProjects(DevMachine devMachine);

    Promise<ProjectConfigDto> getProject(DevMachine devMachine, Path path);

    /**
     * Sends the request to the server to create new file in given {@code path}.
     * <p/>
     * The {@code wsId} should not be a {@code null} or empty, otherwise reject will be occurred.
     * The {@code path} should not be a {@code null} or empty, otherwise reject will be occurred.
     * The {@code content} should not be a {@code null}, otherwise reject will be occurred, otherwise the value may be empty.
     *
     * @param wsId
     *         the workspace id
     * @param path
     *         the file path
     * @param content
     *         the file content
     * @return {@code Promise} with created {@code ItemReference}
     * @since 4.0.0-RC14
     */
    Promise<ItemReference> createFile(DevMachine devMachine, Path path, String content);

    /**
     * Sends the request to the server to create new folder in given {@code path}.
     * <p/>
     * The {@code path} should not be a {@code null} or empty, otherwise reject will be occurred.
     *
     * @param wsId
     *         the workspace id
     * @param path
     *         the folder path
     * @return {@code Promise} with created {@code ItemReference}
     * @since 4.0.0-RC14
     */
    Promise<ItemReference> createFolder(DevMachine devMachine, Path path);

    /**
     * Sends the request to the server to create new project with given {@code config}.
     *
     * @param wsId
     *         the workspace id
     * @param config
     *         the project configuration
     * @return {@code Promise} with created {@code ProjectConfigDto}
     * @since 4.0.0-RC14
     */
    Promise<ProjectConfigDto> createProject(DevMachine devMachine, ProjectConfigDto config);

    /**
     * Sends the request to the server to request info for the item with given {@code path}.
     *
     * @param wsId
     *         the workspace id
     * @param path
     *         the item path to request information
     * @return {@code Promise} with {@code ItemReference}
     * @since 4.0.0-RC14
     */
    Promise<ItemReference> getItem(DevMachine devMachine, Path path);

    /**
     * Estimates if the folder supposed to be project of certain type.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path of the project to estimate
     * @param projectType
     *         Project Type ID to estimate against
     * @param callback
     *         the callback to use for the response
     */
    @Deprecated
    void estimateProject(DevMachine devMachine, String path, String projectType, AsyncRequestCallback<SourceEstimation> callback);

    Promise<SourceEstimation> estimate(DevMachine devMachine, Path path, String projectType);

    Promise<ProjectConfigDto> updateProject(DevMachine devMachine, ProjectConfigDto descriptor);

    /**
     * Sends the request to the server to read the file content by given {@code path}.
     *
     * @param wsId
     *         the workspace id
     * @param path
     *         the file path to read
     * @return {@code Promise} with file content response
     * @since 4.0.0-RC14
     */
    Promise<String> readFile(DevMachine devMachine, Path path);

    /**
     * Sends the request to the server to update the file content by given {@code path}.
     *
     * @param wsId
     *         the workspace id
     * @param path
     *         the file path to be updated
     * @param content
     *         the file content
     * @return {@code Promise} with empty response
     * @since 4.0.0-RC-9
     */
    Promise<Void> writeFile(DevMachine devMachine, Path path, String content);

    /**
     * Sends the request to the server to delete resource with given {@code path}.
     *
     * @param wsId
     *         the workspace id
     * @param path
     *         the path to be removed
     * @return {@code Promise} with empty response
     * @since 4.0.0-RC-9
     */
    Promise<Void> delete(DevMachine devMachine, Path path);

    /**
     * Sends the request to the server to copy given {@code source} to given {@code target}.
     * <p/>
     * The {@code newName} may be omit, in this case name will be saved after move.
     *
     * @param wsId
     *         the workspace id
     * @param source
     *         the source path to be copied
     * @param target
     *         the target path, should be a container (project or folder)
     * @param newName
     *         the new name of the copied item
     * @param overwrite
     *         overwrite target is such has already exists
     * @return {@code Promise} with empty response
     * @since 4.0.0-RC14
     */
    Promise<Void> copy(DevMachine devMachine, Path source, Path target, String newName, boolean overwrite);

    /**
     * Sends the request to the server to move given {@code source} to given {@code target}.
     * <p/>
     * The {@code newName} may be omit, in this case name will be saved after move.
     *
     * @param wsId
     *         the workspace id
     * @param source
     *         the source path to be moved
     * @param target
     *         the target path, should be a container (project or folder)
     * @param newName
     *         the new name of the moved item
     * @param overwrite
     *         overwrite target is such has already exists
     * @return {@code Promise} with empty response
     * @since 4.0.0-RC14
     */
    Promise<Void> move(DevMachine devMachine, Path source, Path target, String newName, boolean overwrite);

    /**
     * Import sources into project.
     *
     * @param workspaceId
     *         id of current workspace
     * @param name
     *         name to the project to import sources
     * @param force
     *         if it's true then rewrites existing project
     * @param sourceStorage
     *         {@link SourceStorageDto}
     * @return a promise that will resolve when the project has been imported, or rejects with an error
     */
    Promise<Void> importProject(DevMachine devMachine, Path path, SourceStorageDto sourceStorage);

    /**
     * Sends the request to the server to read resource tree.
     *
     * @param wsId
     *         the workspace id
     * @param path
     *         the start point path where read should start
     * @param depth
     *         the depth to read
     * @param includeFiles
     *         include files into response
     * @return {@code Promise} with tree response
     * @since 4.0.0-RC14
     */
    Promise<TreeElement> getTree(DevMachine devMachine, Path path, int depth, boolean includeFiles);

    /**
     * Search an item(s) by the specified criteria.
     *
     * @param workspaceId
     *         id of current workspace
     * @param expression
     *         search query expression
     * @return a promise that will provide a list of {@link ItemReference}s, or rejects with an error
     */
    Promise<List<ItemReference>> search(DevMachine devMachine, QueryExpression expression);

    /**
     * Gets list of {@link SourceEstimation} for all supposed project types.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path of the project to resolve
     * @return a promise that will provide a list of {@code SourceEstimation} for the given {@code workspaceId} and {@code path},
     * or rejects with on error
     */
    Promise<List<SourceEstimation>> resolveSources(DevMachine devMachine, Path path);

    /**
     * Update project.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to the project to get
     * @param descriptor
     *         descriptor of the project to update
     * @return a promise that will provide updated {@link ProjectConfigDto} for {@code workspaceId}, {@code path}, {@code descriptor}
     * or rejects with an error
     * @deprecated use {@link #updateProject(String, ProjectConfigDto)}
     */
    @Deprecated
    Promise<ProjectConfigDto> updateProject(DevMachine devMachine, Path path, ProjectConfigDto descriptor);

    /**
     * Rename and/or set new media type for item.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path to the item to rename
     * @param newName
     *         new name
     * @param newMediaType
     *         new media type. May be <code>null</code>
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #move(String, Path, Path, String, boolean)}
     */
    @Deprecated
    void rename(DevMachine devMachine, String path, String newName, String newMediaType, AsyncRequestCallback<Void> callback);

    /**
     * Get project.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to the project
     * @return a promise that resolves to the {@link ProjectConfigDto}, or rejects with an error
     * @deprecated use {@link #getProject(String, Path)}
     */
    @Deprecated
    Promise<ProjectConfigDto> getProject(DevMachine devMachine, String path);

    /**
     * Get children for the specified path.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to get its children
     * @param callback
     *         the callback to use for the response
     */
    @Deprecated
    void getChildren(DevMachine devMachine, String path, AsyncRequestCallback<List<ItemReference>> callback);

    /**
     * Import sources into project.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path to the project to import sources
     * @param force
     *         set true for force rewrite existed project
     * @param sourceStorage
     *         {@link SourceStorageDto}
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #importProject(String, String, boolean, SourceStorageDto)}
     */
    @Deprecated
    void importProject(DevMachine devMachine, String path, boolean force, SourceStorageDto sourceStorage, RequestCallback<Void> callback);

    /**
     * Get all projects in current workspace.
     *
     * @param workspaceId
     *         id of current workspace
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #getProjects(String)}
     */
    @Deprecated
    void getProjects(DevMachine devMachine, AsyncRequestCallback<List<ProjectConfigDto>> callback);

    /**
     * Get project.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to the project to get
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #getProject(String, String)}
     */
    @Deprecated
    void getProject(DevMachine devMachine, String path, AsyncRequestCallback<ProjectConfigDto> callback);

    /**
     * Get item.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path to the item to get
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #getItem(String, Path)}
     */
    @Deprecated
    void getItem(DevMachine devMachine, String path, AsyncRequestCallback<ItemReference> callback);

    /**
     * Create project.
     *
     * @param workspaceId
     *         id of current workspace
     * @param projectConfig
     *         descriptor of the project to create
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #createProject(String, ProjectConfigDto)}
     */
    @Deprecated
    void createProject(DevMachine devMachine, ProjectConfigDto projectConfig, AsyncRequestCallback<ProjectConfigDto> callback);

    /**
     * Get sub-project.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path to the parent project
     * @param callback
     *         the callback to use for the response
     */
    @Deprecated
    void getModules(DevMachine devMachine, String path, AsyncRequestCallback<List<ProjectConfigDto>> callback);

    /**
     * Create sub-project.
     *
     * @param workspaceId
     *         id of current workspace
     * @param parentProjectPath
     *         path to the parent project
     * @param projectConfig
     *         descriptor of the project to create
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #createProject(String, ProjectConfigDto)}
     */
    @Deprecated
    void createModule(DevMachine devMachine,
                      String parentProjectPath,
                      ProjectConfigDto projectConfig,
                      AsyncRequestCallback<ProjectConfigDto> callback);

    /**
     * Update project.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path to the project to get
     * @param descriptor
     *         descriptor of the project to update
     * @param callback
     *         the callback to use for the response
     * @deprecated instead of this method should use {@link ProjectServiceClient#updateProject(String, Path, ProjectConfigDto)}
     */
    @Deprecated
    void updateProject(DevMachine devMachine, String path, ProjectConfigDto descriptor, AsyncRequestCallback<ProjectConfigDto> callback);

    /**
     * Create new file in the specified folder.
     *
     * @param devMachine
     *         of current devMachine
     * @param parentPath
     *         path to parent for new file
     * @param name
     *         file name
     * @param content
     *         file content
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #createFile(String, Path, String)}
     */
    @Deprecated
    void createFile(DevMachine devMachine, String parentPath, String name, String content, AsyncRequestCallback<ItemReference> callback);


    /**
     * Update file content.
     *
     * @param workspaceId
     *         id of current workspace
     * @param path
     *         path to file
     * @param content
     *         new content of file
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #writeFile(String, Path, String)}
     */
    @Deprecated
    void updateFile(DevMachine devMachine, String path, String content, AsyncRequestCallback<Void> callback);

    /**
     * Get file content.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to file
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #readFile(String, Path)}
     */
    @Deprecated
    void getFileContent(DevMachine devMachine, String path, AsyncRequestCallback<String> callback);

    /**
     * Gets list of {@link SourceEstimation} for all supposed project types.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path of the project to resolve
     * @param callback
     *         the callback to use for the response
     * @deprecated instead of this method should use {@link ProjectServiceClient#resolveSources(String, String)}
     */
    @Deprecated
    void resolveSources(DevMachine devMachine, String path, AsyncRequestCallback<List<SourceEstimation>> callback);

    /**
     * Create new folder in the specified folder.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to parent for new folder
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #createFolder(String, Path)}
     */
    @Deprecated
    void createFolder(DevMachine devMachine, String path, AsyncRequestCallback<ItemReference> callback);

    /**
     * Delete item.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to item to delete
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #delete(String, Path)}
     */
    @Deprecated
    void delete(DevMachine devMachine, String path, AsyncRequestCallback<Void> callback);

    /**
     * Delete module.
     *
     * @param workspaceId
     *         id of current workspace
     * @param pathToParent
     *         path to module's parent
     * @param modulePath
     *         path to module to delete
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #delete(String, Path)}
     */
    @Deprecated
    void deleteModule(DevMachine devMachine, String pathToParent, String modulePath, AsyncRequestCallback<Void> callback);

    /**
     * Copy an item with new name to the specified target path. Original item name is used if new name isn't set.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to the item to copy
     * @param newParentPath
     *         path to the target item
     * @param newName
     *         new resource name. Set <code>null</code> to copy without renaming
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #copy(String, Path, Path, String, boolean)}
     */
    @Deprecated
    void copy(DevMachine devMachine, String path, String newParentPath, String newName, AsyncRequestCallback<Void> callback);

    /**
     * Move an item to the specified target path. Set new name to rename the resource when moving.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to the item to move
     * @param newParentPath
     *         path to the target item
     * @param newName
     *         new resource name. Set <code>null</code> to move without renaming
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #move(String, Path, Path, String, boolean)}
     */
    @Deprecated
    void move(DevMachine devMachine, String path, String newParentPath, String newName, AsyncRequestCallback<Void> callback);

    /**
     * Get folders tree starts from the specified path.
     *
     * @param devMachine
     *         of current devMachine
     * @param path
     *         path to get its folder tree
     * @param depth
     *         depth for discover children
     * @param callback
     *         the callback to use for the response
     * @deprecated use {@link #getTree(String, Path, int, boolean)}
     */
    @Deprecated
    void getTree(DevMachine devMachine, String path, int depth, AsyncRequestCallback<TreeElement> callback);
}
