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

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.git.GitServiceClient;
import org.eclipse.che.api.git.shared.Remote;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.workspace.Workspace;
import org.eclipse.che.ide.ext.git.client.GitLocalizationConstant;
import org.eclipse.che.ide.ext.git.client.outputconsole.GitOutputConsole;
import org.eclipse.che.ide.ext.git.client.outputconsole.GitOutputConsoleFactory;
import org.eclipse.che.ide.extension.machine.client.processes.ConsolesPanelPresenter;

import java.util.List;

import static org.eclipse.che.ide.api.notification.StatusNotification.DisplayMode.FLOAT_MODE;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.FAIL;
import static org.eclipse.che.ide.ext.git.client.remote.RemotePresenter.REMOTE_REPO_COMMAND_NAME;

/**
 * Presenter for showing git url.
 *
 * @author Ann Zhuleva
 * @author Oleksii Orel
 * @author Vlad Zhukovskyi
 */
@Singleton
public class ShowProjectGitReadOnlyUrlPresenter implements ShowProjectGitReadOnlyUrlView.ActionDelegate {
    private static final String READ_ONLY_URL_COMMAND_NAME = "Git read only url";

    private final ShowProjectGitReadOnlyUrlView view;
    private final GitOutputConsoleFactory       gitOutputConsoleFactory;
    private final ConsolesPanelPresenter        consolesPanelPresenter;
    private final Workspace                     workspace;
    private final GitServiceClient              service;
    private final AppContext                    appContext;
    private final GitLocalizationConstant       constant;
    private final NotificationManager           notificationManager;

    @Inject
    public ShowProjectGitReadOnlyUrlPresenter(ShowProjectGitReadOnlyUrlView view,
                                              GitServiceClient service,
                                              AppContext appContext,
                                              GitLocalizationConstant constant,
                                              NotificationManager notificationManager,
                                              GitOutputConsoleFactory gitOutputConsoleFactory,
                                              ConsolesPanelPresenter consolesPanelPresenter,
                                              Workspace workspace) {
        this.view = view;
        this.gitOutputConsoleFactory = gitOutputConsoleFactory;
        this.consolesPanelPresenter = consolesPanelPresenter;
        this.workspace = workspace;
        this.view.setDelegate(this);
        this.service = service;
        this.appContext = appContext;
        this.constant = constant;
        this.notificationManager = notificationManager;
    }

    /** Show dialog. */
    public void showDialog(Project project) {
        view.showDialog();

        service.remoteList(appContext.getDevMachine(), project.getLocation(), null, true).then(new Operation<List<Remote>>() {
            @Override
            public void apply(List<Remote> remotes) throws OperationException {
                view.setRemotes(remotes);
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError error) throws OperationException {
                view.setRemotes(null);
                String errorMessage = error.getMessage() != null ? error.getMessage() : constant.remoteListFailed();
                GitOutputConsole console = gitOutputConsoleFactory.create(REMOTE_REPO_COMMAND_NAME);
                console.printError(errorMessage);
                consolesPanelPresenter.addCommandOutput(appContext.getDevMachine().getId(), console);
                notificationManager.notify(constant.remoteListFailed(), FAIL, FLOAT_MODE);
            }
        });

        service.getGitReadOnlyUrl(appContext.getDevMachine(), project.getLocation()).then(new Operation<String>() {
            @Override
            public void apply(String url) throws OperationException {
                view.setLocaleUrl(url);
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError error) throws OperationException {
                String errorMessage = error.getMessage() != null && !error.getMessage().isEmpty()
                                      ? error.getMessage() : constant.initFailed();
                final GitOutputConsole console = gitOutputConsoleFactory.create(READ_ONLY_URL_COMMAND_NAME);
                console.printError(errorMessage);
                consolesPanelPresenter
                        .addCommandOutput(appContext.getDevMachine().getId(), console);
                notificationManager.notify(constant.initFailed(), FAIL, FLOAT_MODE);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public void onCloseClicked() {
        view.close();
    }
}
