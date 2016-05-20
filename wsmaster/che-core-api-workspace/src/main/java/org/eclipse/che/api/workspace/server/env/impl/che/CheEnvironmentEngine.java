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
package org.eclipse.che.api.workspace.server.env.impl.che;

import com.google.common.annotations.VisibleForTesting;

import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.workspace.Environment;
import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.api.machine.server.exception.MachineException;
import org.eclipse.che.api.machine.server.exception.SnapshotException;
import org.eclipse.che.api.machine.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;
import org.eclipse.che.api.workspace.server.env.spi.EnvironmentEngine;
import org.slf4j.Logger;

import javax.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * author Alexander Garagatyi
 */
public class CheEnvironmentEngine implements EnvironmentEngine {
    public final static String ENVIRONMENT_TYPE= "che";

    private static final Logger LOG = getLogger(CheEnvironmentEngine.class);

    private final Map<String, Queue<MachineConfigImpl>> startQueues;
    private final MachineManager                        machineManager;
    private final CheEnvironmentValidator               cheEnvironmentValidator;
    private final Map<String, List<MachineImpl>>        machines;
    private final ReadWriteLock                         rwLock;

    public CheEnvironmentEngine(MachineManager machineManager, CheEnvironmentValidator cheEnvironmentValidator) {
        this.machineManager = machineManager;
        this.cheEnvironmentValidator = cheEnvironmentValidator;
        this.startQueues = new HashMap<>();
        this.machines = new HashMap<>();
        this.rwLock = new ReentrantReadWriteLock();
    }

    @Override
    public String getType() {
        return ENVIRONMENT_TYPE;
    }

    @Override
    public List<Machine> start(String workspaceId, Environment env, boolean recover)
            throws ServerException, NotFoundException, ConflictException, IllegalArgumentException {

        // check old and new environment format
        List<? extends MachineConfig> machineConfigs = cheEnvironmentValidator.parse(env);

        List<MachineConfigImpl> configs = machineConfigs.stream()
                                                        .map(MachineConfigImpl::new)
                                                        .collect(Collectors.toList());

        // Dev machine goes first in the start queue
        final MachineConfigImpl devCfg = rmFirst(configs, MachineConfigImpl::isDev);
        configs.add(0, devCfg);
        rwLock.writeLock().lock();
        try {
            startQueues.put(workspaceId, new ArrayDeque<>(configs));
        } finally {
            rwLock.writeLock().unlock();
        }
        startQueue(workspaceId, env.getName(), recover);
        rwLock.writeLock().lock();
        List<MachineImpl> envMachines;
        try {
            envMachines = this.machines.get(workspaceId);
        } finally {
            rwLock.writeLock().unlock();
        }
        return toListOfMachines(envMachines);
    }

    @Override
    public void stop(String workspaceId) throws NotFoundException, ServerException {
        // remove the workspace from the queue to prevent start
        // of another not started machines(if such exist)
        startQueues.remove(workspaceId);
        List<MachineImpl> machines = this.machines.get(workspaceId);
        if (machines != null && !machines.isEmpty()) {
            destroyRuntime(workspaceId, machines);
        }
    }

    @VisibleForTesting
    void cleanupStartResources(String workspaceId) {
        rwLock.writeLock().lock();
        try {
            machines.remove(workspaceId);
            startQueues.remove(workspaceId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    void removeRuntime(String wsId) {
        rwLock.writeLock().lock();
        try {
            machines.remove(wsId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes all descriptors from the in-memory storage, while
     * {@link MachineManager#cleanup()} is responsible for machines destroying.
     */
    @PreDestroy
    @VisibleForTesting
    void cleanup() {
        startQueues.clear();
    }

    private List<Machine> toListOfMachines(List<MachineImpl> machineImpls) {
        return machineImpls.stream()
                           .collect(ArrayList::new, List::add, List::addAll);
    }

    /**
     * Stops workspace by destroying all its machines and removing it from in memory storage.
     */
    private void destroyRuntime(String wsId, List<MachineImpl> machines) throws NotFoundException, ServerException {
        final MachineImpl devMachine = rmFirst(machines, m -> m.getConfig().isDev());
        // destroying all non-dev machines
        for (MachineImpl machine : machines) {
            try {
                machineManager.destroy(machine.getId(), false);
            } catch (NotFoundException ignore) {
                // it is ok, machine has been already destroyed
            } catch (RuntimeException | MachineException ex) {
                LOG.error(format("Could not destroy machine '%s' of workspace '%s'",
                                 machine.getId(),
                                 machine.getWorkspaceId()),
                          ex);
            }
        }
        // destroying dev-machine
        try {
            machineManager.destroy(devMachine.getId(), false);
        } catch (NotFoundException ignore) {
            // it is ok, machine has been already destroyed
        } finally {
            removeRuntime(wsId);
        }
    }

    private void startQueue(String wsId, String envName, boolean recover) throws ServerException,
                                                                                 NotFoundException,
                                                                                 ConflictException {
        MachineConfigImpl config = getPeekConfig(wsId);
        while (config != null) {
            startMachine(config, wsId, envName, recover);
            config = getPeekConfig(wsId);
        }

        // Clean up the start queue when all the machines successfully started
        rwLock.writeLock().lock();
        try {
            final Queue<MachineConfigImpl> queue = startQueues.get(wsId);
            if (queue != null && queue.isEmpty()) {
                startQueues.remove(wsId);
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private MachineConfigImpl getPeekConfig(String wsId) throws ConflictException, ServerException {
        // Trying to get machine to start. If queue doesn't exist then workspace
        // start was interrupted either by the stop method, or by the cleanup
        rwLock.readLock().lock();
        try {
            final Queue<MachineConfigImpl> queue = startQueues.get(wsId);
            if (queue == null) {
                throw new ConflictException(format("Workspace '%s' start interrupted. " +
                                                   "Workspace was stopped before all its machines were started",
                                                   wsId));
            }
            return queue.peek();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void startMachine(MachineConfigImpl config,
                              String wsId,
                              String envName,
                              boolean recover) throws ServerException,
                                                      NotFoundException,
                                                      ConflictException {
        // Trying to start machine from the given configuration
        MachineImpl machine = null;
        try {
            machine = createMachine(config, wsId, envName, recover);
        } catch (RuntimeException | MachineException | NotFoundException | SnapshotException | ConflictException ex) {
            if (config.isDev()) {
                cleanupStartResources(wsId);
                throw ex;
            }
            LOG.error(format("Error while creating non-dev machine '%s' in workspace '%s', environment '%s'",
                             config.getName(),
                             wsId,
                             envName),
                      ex);
        }

        // Machine destroying is an expensive operation which must be
        // performed outside of the lock, this section checks if
        // the workspace wasn't stopped while it is starting and sets
        // polled flag to true if the workspace wasn't stopped plus
        // polls the proceeded machine configuration from the queue
        boolean queuePolled = false;
        rwLock.readLock().lock();
        try {
//            ensurePreDestroyIsNotExecuted();
            final Queue<MachineConfigImpl> queue = startQueues.get(wsId);
            if (queue != null) {
                queue.poll();
                queuePolled = true;
                if (machine != null) {
                    List<MachineImpl> machines = this.machines.get(wsId);
                    machines.add(machine);
                }
            }
        } finally {
            rwLock.readLock().unlock();
        }

        // If machine config is not polled from the queue
        // then stop method was executed and the machine which
        // has been just created must be destroyed
        if (!queuePolled) {
            if (machine != null) {
                machineManager.destroy(machine.getId(), false);
            }
            throw new ConflictException(format("Workspace '%s' start interrupted. " +
                                               "Workspace was stopped before all its machines were started",
                                               wsId));
        }
    }

    private <T> T rmFirst(List<? extends T> elements, Predicate<T> predicate) {
        T element = null;
        for (final Iterator<? extends T> it = elements.iterator(); it.hasNext() && element == null; ) {
            final T next = it.next();
            if (predicate.test(next)) {
                element = next;
                it.remove();
            }
        }
        return element;
    }

    /**
     * Creates or recovers machine based on machine config.
     */
    private MachineImpl createMachine(MachineConfig machine,
                                      String workspaceId,
                                      String envName,
                                      boolean recover) throws MachineException,
                                                              SnapshotException,
                                                              NotFoundException,
                                                              ConflictException {
        try {
            if (recover) {
                return machineManager.recoverMachine(machine, workspaceId, envName);
            } else {
                return machineManager.createMachineSync(machine, workspaceId, envName);
            }
        } catch (BadRequestException brEx) {
            // TODO fix this in machineManager
            throw new IllegalArgumentException(brEx.getLocalizedMessage(), brEx);
        }
    }
}
