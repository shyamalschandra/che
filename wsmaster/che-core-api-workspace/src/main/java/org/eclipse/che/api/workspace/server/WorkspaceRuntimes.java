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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.core.model.workspace.Environment;
import org.eclipse.che.api.core.model.workspace.WorkspaceRuntime;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.core.notification.EventService;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;
import org.eclipse.che.api.workspace.server.env.impl.che.CheEnvironmentEngine;
import org.eclipse.che.api.workspace.server.env.spi.EnvironmentEngine;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceRuntimeImpl;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent;
import org.eclipse.che.api.workspace.shared.dto.event.WorkspaceStatusEvent.EventType;
import org.slf4j.Logger;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.eclipse.che.dto.server.DtoFactory.newDto;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Defines an internal API for managing {@link WorkspaceRuntimeImpl} instances.
 *
 * <p>This component implements {@link WorkspaceStatus} spec.
 *
 * <p>All the operations performed by this component are synchronous.
 *
 * <p>The implementation is thread-safe and guarded by {@link ReentrantReadWriteLock rwLock}.
 *
 * <p>The implementation doesn't validate parameters.
 * Parameters should be validated by caller of methods of this class.
 *
 * @author Yevhenii Voevodin
 * @author Alexander Garagatyi
 */
@Singleton
public class WorkspaceRuntimes {
    private static final Logger LOG = getLogger(WorkspaceRuntimes.class);

    private final ReadWriteLock                  rwLock;
    private final Map<String, RuntimeDescriptor> descriptors;
    private final EventService                   eventService;
    private final Map<String, EnvironmentEngine> envEngines;

    private volatile boolean isPreDestroyInvoked;

    @Inject
    public WorkspaceRuntimes(EventService eventService, Map<String, EnvironmentEngine> envEngines) {
        this.eventService = eventService;
        this.envEngines = envEngines;
        this.descriptors = new HashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
    }

    /**
     * Returns the runtime descriptor describing currently starting/running/stopping
     * workspace runtime.
     *
     * <p>Note that the {@link RuntimeDescriptor#getRuntime()} method
     * returns {@link Optional} which describes just a snapshot copy of
     * a real {@code WorkspaceRuntime} object, which means that any
     * runtime copy modifications won't affect the real object and also
     * it means that copy won't be affected with modifications applied
     * to the real runtime workspace object state.
     *
     * @param workspaceId
     *         the id of the workspace to get its runtime
     * @return descriptor which describes current state of the workspace runtime
     * @throws NotFoundException
     *         when workspace with given {@code workspaceId} doesn't have runtime
     */
    public RuntimeDescriptor get(String workspaceId) throws NotFoundException {
        rwLock.readLock().lock();
        try {
            final RuntimeDescriptor descriptor = descriptors.get(workspaceId);
            if (descriptor == null) {
                throw new NotFoundException("Workspace with id '" + workspaceId + "' is not running.");
            }
            return new RuntimeDescriptor(descriptor);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Starts all machines from specified workspace environment,
     * creates workspace runtime instance based on that environment.
     *
     * <p>During the start of the workspace its
     * runtime is visible with {@link WorkspaceStatus#STARTING} status.
     *
     * @param workspace
     *         workspace which environment should be started
     * @param envName
     *         the name of the environment to start
     * @param recover
     *         whether machines should be recovered(true) or not(false)
     * @return the workspace runtime instance with machines set.
     * @throws ConflictException
     *         when workspace is already running
     * @throws ConflictException
     *         when start is interrupted
     * @throws NotFoundException
     *         when any not found exception occurs during environment start
     * @throws ServerException
     *         when component {@link #isPreDestroyInvoked is stopped} or any
     *         other error occurs during environment start
     * @see EnvironmentEngine#start(String, Environment, boolean)
     * @see WorkspaceStatus#STARTING
     * @see WorkspaceStatus#RUNNING
     */
    public RuntimeDescriptor start(WorkspaceImpl workspace, String envName, boolean recover) throws ServerException,
                                                                                                    ConflictException,
                                                                                                    NotFoundException {
        Optional<EnvironmentImpl> envOptional = workspace.getConfig().getEnvironment(envName);
        if (!envOptional.isPresent()) {
            throw new IllegalArgumentException("Environment with name " + envName + " not found");
        }
        final EnvironmentImpl activeEnv = new EnvironmentImpl(envOptional.get());
        // todo update all existing environments with type 'che'
        if (activeEnv.getType() == null) {
            activeEnv.setType("che");
        }
        EnvironmentEngine engine = envEngines.get(activeEnv.getType());
        if (engine == null) {
            throw new NotFoundException("Environment engine of type '" + activeEnv.getType() + "' is not found");
        }

        rwLock.writeLock().lock();
        try {
            final RuntimeDescriptor descriptor = descriptors.get(workspace.getId());
            if (descriptor != null) {
                throw new ConflictException(format("Could not start workspace '%s' because its status is '%s'",
                                                   workspace.getConfig().getName(),
                                                   descriptor.getRuntimeStatus()));
            }
            descriptors.put(workspace.getId(), new RuntimeDescriptor(new WorkspaceRuntimeImpl(envName, activeEnv.getType())));
        } finally {
            rwLock.writeLock().unlock();
        }

        // todo should we declare that environment should start dev machine or not?
        ensurePreDestroyIsNotExecuted();
        publishEvent(EventType.STARTING, workspace.getId(), null);
        List<Machine> machines = engine.start(workspace.getId(), activeEnv, recover);
        List<MachineImpl> machinesImpls = machines.stream()
                                                  .map(MachineImpl::new)
                                                  .collect(Collectors.toList());
        Optional<MachineImpl> devMachineOpt = machinesImpls.stream()
                                                           .filter(machine -> machine.getConfig().isDev())
                                                           .findAny();
        if (!devMachineOpt.isPresent()) {
            publishEvent(EventType.ERROR,
                         workspace.getId(),
                         "Environment " + envName + " has booted but it doesn't contain dev machine. Environment has been stopped.");
            try {
                engine.stop(workspace.getId());
            } catch (Exception e) {
                LOG.error(e.getLocalizedMessage(), e);
            }
            throw new ServerException("Environment " + envName +
                                      " has booted but it doesn't contain dev machine. Environment has been stopped.");
        } else {
            publishEvent(EventType.RUNNING, workspace.getId(), null);
            rwLock.writeLock().lock();
            try {
                WorkspaceRuntimeImpl runtime = descriptors.get(workspace.getId()).getRuntime();

                runtime.setMachines(machinesImpls);
                runtime.setDevMachine(devMachineOpt.get());
            } finally {
                rwLock.writeLock().unlock();
            }
            return get(workspace.getId());
        }
    }

    /**
     * This method is similar to the {@link #start(WorkspaceImpl, String, boolean)} method
     * except that it doesn't recover workspace and always starts a new one.
     */
    public RuntimeDescriptor start(WorkspaceImpl workspace, String envName) throws ServerException,
                                                                                   ConflictException,
                                                                                   NotFoundException {
        return start(workspace, envName, false);
    }


    /**
     * Stops running workspace runtime.
     *
     * <p>Stops environment in an implementation specific way.
     * During the stop of the workspace its runtime is accessible with {@link WorkspaceStatus#STOPPING stopping} status.
     * Workspace may be stopped only if its status is {@link WorkspaceStatus#RUNNING}.
     *
     * @param workspaceId
     *         identifier of workspace which should be stopped
     * @throws NotFoundException
     *         when workspace with specified identifier is not running
     * @throws ServerException
     *         when any error occurs during workspace stopping
     * @throws ConflictException
     *         when running workspace status is different from {@link WorkspaceStatus#RUNNING}
     * @see CheEnvironmentEngine#stop(String)
     * @see WorkspaceStatus#STOPPING
     */
    public void stop(String workspaceId) throws NotFoundException, ServerException, ConflictException {
        EnvironmentEngine engine;
        rwLock.writeLock().lock();
        try {
            ensurePreDestroyIsNotExecuted();
            final RuntimeDescriptor descriptor = descriptors.get(workspaceId);
            if (descriptor == null) {
                throw new NotFoundException("Workspace with id '" + workspaceId + "' is not running.");
            }
            if (descriptor.getRuntimeStatus() != WorkspaceStatus.RUNNING) {
                throw new ConflictException(format("Couldn't stop '%s' workspace because its status is '%s'",
                                                   workspaceId,
                                                   descriptor.getRuntimeStatus()));
            }
            descriptor.setStopping();
            engine = envEngines.get(descriptor.getRuntime().getEnvType());
        } finally {
            rwLock.writeLock().unlock();
        }

        publishEvent(EventType.STOPPING, workspaceId, null);
        try {
            engine.stop(workspaceId);
            publishEvent(EventType.STOPPED, workspaceId, null);
        } catch (NotFoundException | ServerException | RuntimeException e) {
            publishEvent(EventType.ERROR, workspaceId, e.getLocalizedMessage());
        } finally {
            removeRuntime(workspaceId);
        }
    }

    /**
     * Returns true if workspace was started and its status is
     * {@link WorkspaceStatus#RUNNING running}, {@link WorkspaceStatus#STARTING starting}
     * or {@link WorkspaceStatus#STOPPING stopping} - otherwise returns false.
     *
     * <p> This method is less expensive alternative to {@link #get(String)} + {@code try catch}, see example:
     * <pre>{@code
     *
     *     if (!runtimes.hasRuntime("workspace123")) {
     *         doStuff("workspace123");
     *     }
     *
     *     //vs
     *
     *     try {
     *         runtimes.get("workspace123");
     *     } catch (NotFoundException ex) {
     *         doStuff("workspace123");
     *     }
     *
     * }</pre>
     *
     * @param workspaceId
     *         workspace identifier to perform check
     * @return true if workspace is running, otherwise false
     */
    public boolean hasRuntime(String workspaceId) {
        rwLock.readLock().lock();
        try {
            return descriptors.containsKey(workspaceId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Removes all descriptors from the in-memory storage, while
     * {@link CheEnvironmentEngine} is responsible for environment destroying.
     */
    @PreDestroy
    @VisibleForTesting
    void cleanup() {
        isPreDestroyInvoked = true;
        final ExecutorService stopEnvExecutor =
                Executors.newFixedThreadPool(2 * Runtime.getRuntime().availableProcessors(),
                                             new ThreadFactoryBuilder().setNameFormat("StopEnvironment-%d")
                                                                       .setDaemon(false)
                                                                       .build());
        rwLock.writeLock().lock();
        // todo inside lock or outside?
        try {

            for (Map.Entry<String, RuntimeDescriptor> descriptorEntry : descriptors.entrySet()) {
                if (descriptorEntry.getValue().getRuntimeStatus().equals(WorkspaceStatus.RUNNING) ||
                    descriptorEntry.getValue().getRuntimeStatus().equals(WorkspaceStatus.RUNNING)) {
                    envEngines.get(descriptorEntry.getValue().getRuntime().getEnvType()).stop(descriptorEntry.getKey());
                }
            }

            descriptors.clear();

            stopEnvExecutor.shutdown();
            if (!stopEnvExecutor.awaitTermination(50, TimeUnit.SECONDS)) {
                stopEnvExecutor.shutdownNow();
                if (!stopEnvExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOG.warn("Unable terminate destroy machines pool");
                }
            }
        } catch (NotFoundException ignore) {
        } catch (ServerException e) {
            LOG.error(e.getLocalizedMessage(), e);
        } catch (InterruptedException e) {
            stopEnvExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    void publishEvent(EventType type, String workspaceId, String error) {
        eventService.publish(newDto(WorkspaceStatusEvent.class)
                                     .withEventType(type)
                                     .withWorkspaceId(workspaceId)
                                     .withError(error));
    }

    @VisibleForTesting
    void cleanupStartResources(String workspaceId) {
        rwLock.writeLock().lock();
        try {
            descriptors.remove(workspaceId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    void removeRuntime(String wsId) {
        rwLock.writeLock().lock();
        try {
            descriptors.remove(wsId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void ensurePreDestroyIsNotExecuted() throws ServerException {
        if (isPreDestroyInvoked) {
            throw new ServerException("Could not perform operation because application server is stopping");
        }
    }

    /**
     * Wrapper for the {@link WorkspaceRuntime} instance.
     * Knows the state of the started workspace runtime,
     * helps to postpone {@code WorkspaceRuntime} instance creation to
     * the time when all the machines from the workspace are created.
     */
    public static class RuntimeDescriptor {

        private WorkspaceRuntimeImpl runtime;
        private boolean              isStopping;

        private RuntimeDescriptor(WorkspaceRuntimeImpl runtime) {
            this.runtime = runtime;
        }

        private RuntimeDescriptor(RuntimeDescriptor descriptor) {
            this(new WorkspaceRuntimeImpl(descriptor.runtime));
            this.isStopping = descriptor.isStopping;
        }

        /**
         * Returns an {@link Optional} describing a started {@link WorkspaceRuntime},
         * if the runtime is in starting state then an empty {@code Optional} will be returned.
         */
        public WorkspaceRuntimeImpl getRuntime() {
            return runtime;
        }

        /**
         * Returns the status of the started workspace runtime.
         * The relation between {@link #getRuntime()} and this method
         * is pretty clear, whether workspace is in starting state, then
         * {@code getRuntime()} will return an empty optional, otherwise
         * the optional describing a running or stopping workspace runtime.
         */
        public WorkspaceStatus getRuntimeStatus() {
            if (isStopping) {
                return WorkspaceStatus.STOPPING;
            }
            if (runtime.getDevMachine() == null) {
                return WorkspaceStatus.STARTING;
            }
            return WorkspaceStatus.RUNNING;
        }

        private void setStopping() {
            isStopping = true;
        }
    }
}
