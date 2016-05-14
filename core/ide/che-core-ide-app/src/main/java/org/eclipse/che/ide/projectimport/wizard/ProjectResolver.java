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
package org.eclipse.che.ide.projectimport.wizard;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.project.shared.dto.SourceEstimation;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseProvider;
import org.eclipse.che.ide.api.notification.Notification;
import org.eclipse.che.ide.api.notification.NotificationListener;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.notification.StatusNotification;
import org.eclipse.che.ide.api.project.MutableProjectConfig;
import org.eclipse.che.ide.api.project.type.ProjectTypeRegistry;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.projecttype.wizard.presenter.ProjectWizardPresenter;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.che.ide.api.notification.StatusNotification.Status.SUCCESS;

/**
 * The class contains business logic which allows resolve project type and call updater.
 *
 * @author Dmitry Shnurenko
 * @author Vlad Zhukovskyi
 */
@Singleton
public class ProjectResolver {

    private final ProjectTypeRegistry    projectTypeRegistry;
    private final PromiseProvider        promiseProvider;
    private final ProjectWizardPresenter projectWizard;
    private final NotificationManager    notificationManager;

    @Inject
    public ProjectResolver(ProjectTypeRegistry projectTypeRegistry,
                           PromiseProvider promiseProvider,
                           ProjectWizardPresenter projectWizard,
                           NotificationManager notificationManager) {
        this.projectTypeRegistry = projectTypeRegistry;
        this.promiseProvider = promiseProvider;
        this.projectWizard = projectWizard;
        this.notificationManager = notificationManager;
    }

    public Promise<Project> resolve(final Project project) {
        return project.resolve().thenPromise(new Function<List<SourceEstimation>, Promise<Project>>() {
            @Override
            public Promise<Project> apply(List<SourceEstimation> estimations) throws FunctionException {
                if (estimations == null || estimations.isEmpty()) {
                    return promiseProvider.resolve(project);
                }

                final List<String> primeTypes = newArrayList();
                for (SourceEstimation estimation : estimations) {
                    if (projectTypeRegistry.getProjectType(estimation.getType()).isPrimaryable()) {
                        primeTypes.add(estimation.getType());
                    }
                }

                final MutableProjectConfig config = new MutableProjectConfig(project);

                if (primeTypes.isEmpty()) {
                    return promiseProvider.resolve(project);
                } else if (primeTypes.size() == 1) {
                    config.setType(primeTypes.get(0));
                } else {
                    final NotificationListener notificationListener = new NotificationListener() {
                        boolean clicked = false;

                        @Override
                        public void onClick(Notification notification) {
                            if (!clicked) {
                                projectWizard.show(config);
                                clicked = true;
                                notification.setListener(null);
                                notification.setContent("");
                            }
                        }

                        @Override
                        public void onDoubleClick(Notification notification) {
                            //stub
                        }

                        @Override
                        public void onClose(Notification notification) {
                            //stub
                        }
                    };

                    notificationManager.notify("Project " + project.getName() + " has to be configured",
                                               "Click here to set up your project.",
                                               SUCCESS, StatusNotification.DisplayMode.FLOAT_MODE, notificationListener);


                    return promiseProvider.resolve(project);
                }

                return project.update().withBody(config).send();
            }
        });
    }
}
