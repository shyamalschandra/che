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
package org.eclipse.che.api.workspace.server;

import com.google.common.base.Joiner;

import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.model.machine.Command;
import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.machine.ServerConf;
import org.eclipse.che.api.core.model.workspace.Environment;
import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.machine.server.MachineInstanceProviders;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;

/**
 * Default implementation of {@link WorkspaceValidator}.
 *
 * @author Yevhenii Voevodin
 */
@Singleton
public class DefaultWorkspaceValidator implements WorkspaceValidator {
    /* should contain [3, 20] characters, first and last character is letter or digit, available characters {A-Za-z0-9.-_}*/
    private static final Pattern WS_NAME         = Pattern.compile("[a-zA-Z0-9][-_.a-zA-Z0-9]{1,18}[a-zA-Z0-9]");
    private static final Pattern SERVER_PORT     = Pattern.compile("[1-9]+[0-9]*/(?:tcp|udp)");
    private static final Pattern SERVER_PROTOCOL = Pattern.compile("[a-z][a-z0-9-+.]*");
    
    private final MachineInstanceProviders machineInstanceProviders;
    
    @Inject
    public DefaultWorkspaceValidator(MachineInstanceProviders machineInstanceProviders) {
    	this.machineInstanceProviders = machineInstanceProviders;
    }

    @Override
    public void validateWorkspace(Workspace workspace) throws BadRequestException {
        validateAttributes(workspace.getAttributes());
        validateConfig(workspace.getConfig());
    }

    @Override
    public void validateConfig(WorkspaceConfig config) throws BadRequestException {
        // configuration object itself
        checkNotNull(config.getName(), "Workspace name required");
        checkArgument(WS_NAME.matcher(config.getName()).matches(),
                      "Incorrect workspace name, it must be between 3 and 20 characters and may contain digits, " +
                      "latin letters, underscores, dots, dashes and should start and end only with digits, " +
                      "latin letters or underscores");


        //environments
        checkArgument(!isNullOrEmpty(config.getDefaultEnv()), "Workspace default environment name required");
        checkArgument(config.getEnvironments()
                            .stream()
                            .anyMatch(env -> config.getDefaultEnv().equals(env.getName())),
                      "Workspace default environment configuration required");

        for (Environment environment : config.getEnvironments()) {
            validateEnv(environment, config.getName());
        }

        //commands
        for (Command command : config.getCommands()) {
            checkArgument(!isNullOrEmpty(command.getName()),
                          "Workspace %s contains command with null or empty name",
                          config.getName());
            checkArgument(!isNullOrEmpty(command.getCommandLine()),
                          "Command line required for command '%s' in workspace '%s'",
                          command.getName(),
                          config.getName());
        }

        //projects
        //TODO
    }

    @Override
    public void validateAttributes(Map<String, String> attributes) throws BadRequestException {
        for (String attributeName : attributes.keySet()) {
            //attribute name should not be empty and should not start with codenvy
            checkArgument(attributeName != null && !attributeName.trim().isEmpty() && !attributeName.toLowerCase().startsWith("codenvy"),
                          "Attribute name '%s' is not valid",
                          attributeName);
        }
    }

    private void validateEnv(Environment environment, String workspaceName) throws BadRequestException {
        final String envName = environment.getName();
        checkArgument(!isNullOrEmpty(envName), "Environment name should be neither null nor empty");

        //machine configs
        checkArgument(!environment.getMachineConfigs().isEmpty(), "Environment '%s' should contain at least 1 machine", envName);

        final long devCount = environment.getMachineConfigs()
                                         .stream()
                                         .filter(MachineConfig::isDev)
                                         .count();
        checkArgument(devCount == 1,
                      "Environment should contain exactly 1 dev machine, but '%s' contains '%d'",
                      envName,
                      devCount);
        for (MachineConfig machineCfg : environment.getMachineConfigs()) {
            validateMachine(machineCfg, envName);
        }
    }

    private void validateMachine(MachineConfig machineCfg, String envName) throws BadRequestException {
        checkArgument(!isNullOrEmpty(machineCfg.getName()), "Environment %s contains machine with null or empty name", envName);
        checkNotNull(machineCfg.getSource(), "Environment " + envName + " contains machine without source");
        checkArgument(!(machineCfg.getSource().getContent() == null && machineCfg.getSource().getLocation() == null),
                      "Environment " + envName + " contains machine with source but this source doesn't define a location or content");

        checkArgument(machineInstanceProviders.hasProvider(machineCfg.getType()),
                      "Type %s of machine %s in environment %s is not supported. Supported values: %s.",
                      machineCfg.getType(),
                      machineCfg.getName(),
                      envName,
                      Joiner.on(", ").join(machineInstanceProviders.getProviderTypes()));

        for (ServerConf serverConf : machineCfg.getServers()) {
            checkArgument(serverConf.getPort() != null && SERVER_PORT.matcher(serverConf.getPort()).matches(),
                          "Machine %s contains server conf with invalid port %s",
                          machineCfg.getName(),
                          serverConf.getPort());
            checkArgument(serverConf.getProtocol() == null || SERVER_PROTOCOL.matcher(serverConf.getProtocol()).matches(),
                          "Machine %s contains server conf with invalid protocol %s",
                          machineCfg.getName(),
                          serverConf.getProtocol());
        }
        for (Map.Entry<String, String> envVariable : machineCfg.getEnvVariables().entrySet()) {
            checkArgument(!isNullOrEmpty(envVariable.getKey()), "Machine %s contains environment variable with null or empty name");
            checkNotNull(envVariable.getValue(), "Machine %s contains environment variable with null value");
        }
    }

    /**
     * Checks that object reference is not null, throws {@link BadRequestException}
     * in the case of null {@code object} with given {@code message}.
     */
    private static void checkNotNull(Object object, String message) throws BadRequestException {
        if (object == null) {
            throw new BadRequestException(message);
        }
    }

    /**
     * Checks that expression is true, throws {@link BadRequestException} otherwise.
     *
     * <p>Exception uses error message built from error message template and error message parameters.
     */
    private static void checkArgument(boolean expression, String errorMessageTemplate, Object... errorMessageParams)
            throws BadRequestException {
        if (!expression) {
            throw new BadRequestException(format(errorMessageTemplate, errorMessageParams));
        }
    }

    /**
     * Checks that expression is true, throws {@link BadRequestException} otherwise.
     *
     * <p>Exception uses error message built from error message template and error message parameters.
     */
    private static void checkArgument(boolean expression, String errorMessage) throws BadRequestException {
        if (!expression) {
            throw new BadRequestException(errorMessage);
        }
    }
}
