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
package org.eclipse.che.ide.ext.java.client.command.valueproviders;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseProvider;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.resources.Project;
import org.eclipse.che.ide.api.resources.Resource;
import org.eclipse.che.ide.ext.java.client.command.ClasspathContainer;
import org.eclipse.che.ide.ext.java.client.project.classpath.ClasspathResolver;
import org.eclipse.che.ide.ext.java.client.util.JavaUtil;
import org.eclipse.che.ide.ext.java.shared.dto.classpath.ClasspathEntryDTO;
import org.eclipse.che.ide.extension.machine.client.command.valueproviders.CommandPropertyValueProvider;

import java.util.List;
import java.util.Set;

/**
 * Provides project's classpath.
 *
 * @author Valeriy Svydenko
 */
@Singleton
public class ClasspathProvider implements CommandPropertyValueProvider {
    private static final String KEY = "${project.java.classpath}";

    private final ClasspathContainer classpathContainer;
    private final ClasspathResolver  classpathResolver;
    private final AppContext         appContext;
    private final PromiseProvider    promises;

    @Inject
    public ClasspathProvider(ClasspathContainer classpathContainer,
                             ClasspathResolver classpathResolver,
                             AppContext appContext,
                             PromiseProvider promises) {
        this.classpathContainer = classpathContainer;
        this.classpathResolver = classpathResolver;
        this.appContext = appContext;
        this.promises = promises;
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public Promise<String> getValue() {

        final Resource[] resources = appContext.getResources();

        if (resources != null && resources.length == 1) {

            final Resource resource = resources[0];
            final Project project = resource.getRelatedProject();

            if (JavaUtil.isJavaProject(project)) {
                return classpathContainer.getClasspathEntries(project.getLocation().toString()).then(
                        new Function<List<ClasspathEntryDTO>, String>() {
                            @Override
                            public String apply(List<ClasspathEntryDTO> arg) throws FunctionException {
                                classpathResolver.resolveClasspathEntries(arg);
                                Set<String> sources = classpathResolver.getSources();
                                StringBuilder classpath = new StringBuilder("");
                                for (String source : sources) {
                                    classpath.append(source);
                                }

                                if (classpath.toString().isEmpty()) {
                                    classpath.append(appContext.getProjectsRoot()).append(project.getLocation().toString());
                                }

                                classpath.append(':');

                                return classpath.toString();
                            }
                        });
            } else {
                return promises.resolve("");
            }
        }

        return promises.resolve("");
    }

}
