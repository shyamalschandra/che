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
package org.eclipse.che.ide.ext.java.client.project.classpath.valueproviders.selectnode;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.data.tree.Node;
import org.eclipse.che.ide.api.data.tree.settings.SettingsProvider;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.workspace.Workspace;
import org.eclipse.che.ide.ext.java.client.project.classpath.valueproviders.pages.ClasspathPagePresenter;
import org.eclipse.che.ide.ext.java.client.project.classpath.valueproviders.selectnode.interceptors.ClasspathNodeInterceptor;
import org.eclipse.che.ide.ext.java.client.project.classpath.valueproviders.selectnode.interceptors.JarNodeInterceptor;
import org.eclipse.che.ide.resources.tree.ResourceNode;
import org.vectomatic.dom.svg.ui.SVGResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Presenter for choosing directory for searching a node.
 *
 * @author Valeriy Svydenko
 */
@Singleton
public class SelectNodePresenter implements SelectNodeView.ActionDelegate {
    private final static String WORKSPACE_PATH = "/projects";

    private final SelectNodeView           view;
    private final ResourceNode.NodeFactory nodeFactory;
    private final SettingsProvider         settingsProvider;
    private final AppContext               appContext;
    private final Workspace                workspace;

    private ClasspathPagePresenter   classpathPagePresenter;
    private ClasspathNodeInterceptor interceptor;

    @Inject
    public SelectNodePresenter(SelectNodeView view,
                               ResourceNode.NodeFactory nodeFactory,
                               SettingsProvider settingsProvider,
                               AppContext appContext,
                               Workspace workspace) {
        this.view = view;
        this.nodeFactory = nodeFactory;
        this.settingsProvider = settingsProvider;
        this.appContext = appContext;
        this.workspace = workspace;
        this.view.setDelegate(this);
    }

    /**
     * Show tree view with all needed nodes of the workspace.
     *
     * @param pagePresenter
     *         delegate from the property page
     * @param nodeInterceptor
     *         interceptor for showing nodes
     * @param forCurrent
     *         if is true - show only current project, otherwise - whole workspace
     */
    public void show(ClasspathPagePresenter pagePresenter, ClasspathNodeInterceptor nodeInterceptor, boolean forCurrent) {
        this.classpathPagePresenter = pagePresenter;
        this.interceptor = nodeInterceptor;
        if (forCurrent) {
            final Project project = appContext.getRootProject();

            view.setStructure(Collections.<Node>singletonList(nodeFactory.newContainerNode(project, settingsProvider.getSettings())), interceptor);
        } else {
            final List<Node> nodes = new ArrayList<>();
            for (Project project : workspace.getProjects()) {
                nodes.add(nodeFactory.newContainerNode(project, settingsProvider.getSettings()));
            }

            view.setStructure(nodes, interceptor);
        }

        view.show();
    }

    /** {@inheritDoc} */
    @Override
    public void setSelectedNode(String path, SVGResource icon) {
        if (interceptor instanceof JarNodeInterceptor) {
            path = WORKSPACE_PATH + path;
        }
        classpathPagePresenter.addNode(path, interceptor.getKind(), icon);
    }
}
