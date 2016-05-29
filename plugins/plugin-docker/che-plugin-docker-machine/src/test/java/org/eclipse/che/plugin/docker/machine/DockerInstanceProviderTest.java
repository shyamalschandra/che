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
package org.eclipse.che.plugin.docker.machine;

import com.google.common.collect.Sets;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.machine.MachineStatus;
import org.eclipse.che.api.core.model.machine.ServerConf;
import org.eclipse.che.api.core.util.LineConsumer;
import org.eclipse.che.api.machine.server.exception.MachineException;
import org.eclipse.che.api.machine.server.model.impl.LimitsImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineSourceImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.api.machine.server.recipe.RecipeImpl;
import org.eclipse.che.api.machine.server.util.RecipeRetriever;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.plugin.docker.client.DockerConnector;
import org.eclipse.che.plugin.docker.client.DockerConnectorConfiguration;
import org.eclipse.che.plugin.docker.client.ProgressMonitor;
import org.eclipse.che.plugin.docker.client.dto.AuthConfigs;
import org.eclipse.che.plugin.docker.client.json.ContainerConfig;
import org.eclipse.che.plugin.docker.client.json.ContainerCreated;
import org.eclipse.che.plugin.docker.client.json.HostConfig;
import org.eclipse.che.plugin.docker.client.params.PullParams;
import org.eclipse.che.plugin.docker.client.params.RemoveImageParams;
import org.eclipse.che.plugin.docker.client.params.TagParams;
import org.eclipse.che.plugin.docker.machine.node.DockerNode;
import org.eclipse.che.plugin.docker.machine.node.WorkspaceFolderPathProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.eclipse.che.plugin.docker.machine.DockerInstanceProvider.DOCKER_FILE_TYPE;
import static org.eclipse.che.plugin.docker.machine.DockerInstanceProvider.DOCKER_IMAGE_TYPE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Listeners(MockitoTestNGListener.class)
public class DockerInstanceProviderTest {
    private static final String  PROJECT_FOLDER_PATH   = "/projects";
    private static final String  CONTAINER_ID          = "containerId";
    private static final String  WORKSPACE_ID          = "wsId";
    private static final String  MACHINE_ID            = "machineId";
    private static final String  MACHINE_NAME          = "machineName";
    private static final String  USER_TOKEN            = "userToken";
    private static final String  USER_NAME             = "user";
    private static final int     MEMORY_LIMIT_MB       = 64;
    private static final boolean SNAPSHOT_USE_REGISTRY = true;

    @Mock
    private DockerConnector dockerConnector;

    @Mock
    private DockerConnectorConfiguration dockerConnectorConfiguration;

    @Mock
    private DockerMachineFactory dockerMachineFactory;

    @Mock
    private DockerInstanceStopDetector dockerInstanceStopDetector;

    @Mock
    private DockerContainerNameGenerator containerNameGenerator;

    @Mock
    private DockerNode dockerNode;

    @Mock
    private WorkspaceFolderPathProvider workspaceFolderPathProvider;

    @Captor
    private ArgumentCaptor<ContainerConfig> containerConfigArgumentCaptor;

    @Mock
    private RecipeRetriever recipeRetriever;

    private DockerInstanceProvider dockerInstanceProvider;

    @BeforeMethod
    public void setUp() throws Exception {
        when(dockerConnectorConfiguration.getDockerHostIp()).thenReturn("123.123.123.123");

        dockerInstanceProvider = spy(new DockerInstanceProvider(dockerConnector,
                                                                dockerConnectorConfiguration,
                                                                dockerMachineFactory,
                                                                dockerInstanceStopDetector,
                                                                containerNameGenerator,
                                                                recipeRetriever,
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                null,
                                                                workspaceFolderPathProvider,
                                                                PROJECT_FOLDER_PATH,
                                                                false,
                                                                false,
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                SNAPSHOT_USE_REGISTRY));

        EnvironmentContext envCont = new EnvironmentContext();
        envCont.setSubject(new SubjectImpl(USER_NAME, "userId", USER_TOKEN, null, false));
        envCont.setWorkspaceId(WORKSPACE_ID);
        EnvironmentContext.setCurrent(envCont);


        when(recipeRetriever.getRecipe(any(MachineConfig.class))).thenReturn(new RecipeImpl().withType(DOCKER_FILE_TYPE).withScript("FROM codenvy"));

        when(dockerMachineFactory.createNode(anyString(), anyString())).thenReturn(dockerNode);
        when(dockerConnector.createContainer(any(ContainerConfig.class), anyString()))
                .thenReturn(new ContainerCreated(CONTAINER_ID, new String[0]));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        EnvironmentContext.reset();
    }

    @Test
    public void shouldReturnTypeDocker() throws Exception {
        assertEquals(dockerInstanceProvider.getType(), "docker");
    }

    @Test
    public void shouldReturnRecipeTypesDockerfile() throws Exception {
        assertEquals(dockerInstanceProvider.getRecipeTypes(), Sets.newHashSet(DOCKER_FILE_TYPE, DOCKER_IMAGE_TYPE));
    }

    // TODO add tests for instance snapshot removal

    @Test
    public void shouldBuildDockerfileOnInstanceCreationFromRecipe() throws Exception {
        String generatedContainerId = "genContainerId";
        doReturn(generatedContainerId).when(containerNameGenerator).generateContainerName(eq(WORKSPACE_ID),
                                                                                          eq(MACHINE_ID),
                                                                                          eq(USER_NAME),
                                                                                          eq(MACHINE_NAME));


        createInstanceFromRecipe();


        verify(dockerConnector).buildImage(eq("eclipse-che/" + generatedContainerId),
                                           any(ProgressMonitor.class),
                                           any(AuthConfigs.class),
                                           anyBoolean(),
                                           eq((long)MEMORY_LIMIT_MB * 1024 * 1024),
                                           eq((long)-1),
                                           anyVararg());
    }

    @Test
    public void shouldPullDockerImageOnInstanceCreationFromSnapshot() throws Exception {
        String repo = "repo";
        String tag = "latest";
        String registry = "localhost:1234";


        createInstanceFromSnapshot(repo, tag, registry);

        PullParams pullParams = PullParams.create(repo).withRegistry(registry).withTag(tag);

        verify(dockerConnector).pull(eq(pullParams), any(ProgressMonitor.class));
    }

    @Test
    public void shouldUseLocalImageOnInstanceCreationFromSnapshot() throws Exception {
        final String repo = "repo";
        final String tag = "latest";
        dockerInstanceProvider = getDockerInstanceProvider(false);

        MachineImpl machine = getMachineBuilder().build();
        final MachineSourceImpl machineSource = new DockerMachineSource(repo).withTag(tag);
        machine.getConfig().setSource(machineSource);

        dockerInstanceProvider.createInstance(machine,
                                              LineConsumer.DEV_NULL);

        verify(dockerConnector, never()).pull(anyString(),
                                              anyString(),
                                              anyString(),
                                              any(ProgressMonitor.class));
    }

    @Test
    public void shouldRemoveLocalImageDuringRemovalOfSnapshot() throws Exception {
        final String repo = "repo";
        final String tag = "latest";
        final DockerMachineSource instanceKey = new DockerMachineSource(repo).withTag(tag);
        dockerInstanceProvider = getDockerInstanceProvider(false);

        dockerInstanceProvider.removeInstanceSnapshot(instanceKey);

        verify(dockerConnector, times(1)).removeImage(RemoveImageParams.create(instanceKey.getFullName()));
    }

    @Test
    public void shouldReTagBuiltImageWithPredictableOnInstanceCreationFromRecipe() throws Exception {
        String generatedContainerId = "genContainerId";
        doReturn(generatedContainerId).when(containerNameGenerator).generateContainerName(eq(WORKSPACE_ID),
                                                                                          eq(MACHINE_ID),
                                                                                          eq(USER_NAME),
                                                                                          eq(MACHINE_NAME));
        String repo = "repo1";
        String tag = "tag1";
        String registry = "registry1";
        TagParams tagParams = TagParams.create(registry + "/" + repo + ":" + tag, "eclipse-che/" + generatedContainerId);

        createInstanceFromSnapshot(repo, tag, registry);

        verify(dockerConnector).tag(eq(tagParams));
        verify(dockerConnector).removeImage(eq(registry + "/" + repo + ":" + tag), eq(false));
    }

    @Test
    public void shouldCreateContainerOnInstanceCreationFromRecipe() throws Exception {
        String generatedContainerId = "genContainerId";
        doReturn(generatedContainerId).when(containerNameGenerator).generateContainerName(eq(WORKSPACE_ID),
                                                                                          eq(MACHINE_ID),
                                                                                          eq(USER_NAME),
                                                                                          eq(MACHINE_NAME));


        createInstanceFromRecipe();


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertEquals(argumentCaptor.getValue().getImage(), "eclipse-che/" + generatedContainerId);
    }

    @Test
    public void shouldStartContainerOnCreateInstanceFromRecipe() throws Exception {
        createInstanceFromRecipe();

        verify(dockerConnector).startContainer(eq(CONTAINER_ID), any(HostConfig.class));
    }

    @Test
    public void shouldCreateContainerOnInstanceCreationFromSnapshot() throws Exception {
        String generatedContainerId = "genContainerId";
        doReturn(generatedContainerId).when(containerNameGenerator).generateContainerName(eq(WORKSPACE_ID),
                                                                                          eq(MACHINE_ID),
                                                                                          eq(USER_NAME),
                                                                                          eq(MACHINE_NAME));
        createInstanceFromSnapshot();


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertEquals(argumentCaptor.getValue().getImage(), "eclipse-che/" + generatedContainerId);
    }

    @Test
    public void shouldCreateContainerWithPrivilegeMode() throws Exception {
        dockerInstanceProvider = spy(new DockerInstanceProvider(dockerConnector,
                                                                dockerConnectorConfiguration,
                                                                dockerMachineFactory,
                                                                dockerInstanceStopDetector,
                                                                containerNameGenerator,
                                                                recipeRetriever,
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                null,
                                                                workspaceFolderPathProvider,
                                                                PROJECT_FOLDER_PATH,
                                                                false,
                                                                true,
                                                                Collections.emptySet(),
                                                                Collections.emptySet(),
                                                                SNAPSHOT_USE_REGISTRY));

        createInstanceFromRecipe();

        verify(dockerConnector).createContainer(containerConfigArgumentCaptor.capture(), anyString());
        assertTrue(containerConfigArgumentCaptor.getValue().getHostConfig().isPrivileged());
    }

    @Test
    public void shouldStartContainerOnCreateInstanceFromSnapshot() throws Exception {
        createInstanceFromSnapshot();

        verify(dockerConnector).startContainer(eq(CONTAINER_ID), any(HostConfig.class));
    }

    @Test
    public void shouldCallCreationDockerInstanceWithFactoryOnCreateInstanceFromSnapshot() throws Exception {
        String generatedContainerId = "genContainerId";
        doReturn(generatedContainerId).when(containerNameGenerator).generateContainerName(eq(WORKSPACE_ID),
                                                                                          eq(MACHINE_ID),
                                                                                          eq(USER_NAME),
                                                                                          eq(MACHINE_NAME));

        final MachineSourceImpl machineSource = new MachineSourceImpl("type").setLocation("location");
        final MachineImpl machine =
                new MachineImpl(new MachineConfigImpl(false,
                                                      MACHINE_NAME,
                                                      "machineType",
                                                      machineSource,
                                                      new LimitsImpl(MEMORY_LIMIT_MB),
                                                      asList(new ServerConfImpl("ref1", "8080", "https", null),
                                                             new ServerConfImpl("ref2", "9090/udp", "someprotocol", null)),
                                                      Collections.singletonMap("key1", "value1")),
                                "machineId",
                                WORKSPACE_ID,
                                "envName",
                                USER_NAME,
                                MachineStatus.CREATING,
                                null);


        createInstanceFromSnapshot(machine);


        verify(dockerMachineFactory).createInstance(eq(machine),
                                                    eq(CONTAINER_ID),
                                                    eq("eclipse-che/" + generatedContainerId),
                                                    eq(dockerNode),
                                                    any(LineConsumer.class));
    }



    @Test
    public void shouldCallCreationDockerInstanceWithFactoryOnCreateInstanceFromRecipe() throws Exception {
        String generatedContainerId = "genContainerId";
        doReturn(generatedContainerId).when(containerNameGenerator).generateContainerName(eq(WORKSPACE_ID),
                                                                                          eq(MACHINE_ID),
                                                                                          eq(USER_NAME),
                                                                                          eq(MACHINE_NAME));

        final MachineSourceImpl machineSource = new MachineSourceImpl(DOCKER_FILE_TYPE).setLocation("location");
        final MachineImpl machine =
                new MachineImpl(new MachineConfigImpl(false,
                                                      MACHINE_NAME,
                                                      "machineType",
                                                      machineSource,
                                                      new LimitsImpl(MEMORY_LIMIT_MB),
                                                      asList(new ServerConfImpl("ref1", "8080", "https", null),
                                                             new ServerConfImpl("ref2", "9090/udp", "someprotocol", null)),
                                                      Collections.singletonMap("key1", "value1")),
                                "machineId",
                                WORKSPACE_ID,
                                "envName",
                                USER_NAME,
                                MachineStatus.CREATING,
                                null);

        createInstanceFromRecipe(machine);


        verify(dockerMachineFactory).createInstance(eq(machine),
                                                    eq(CONTAINER_ID),
                                                    eq("eclipse-che/" + generatedContainerId),
                                                    eq(dockerNode),
                                                    any(LineConsumer.class));
    }

    @Test
    public void shouldBindWorkspaceOnDevInstanceCreationFromRecipe() throws Exception {
        final boolean isDev = true;
        final String hostProjectsFolder = "/tmp/projects";

        when(dockerNode.getProjectsFolder()).thenReturn(hostProjectsFolder);

        createInstanceFromRecipe(isDev, WORKSPACE_ID);

        verify(dockerNode).bindWorkspace();
    }

    @Test
    public void shouldBindWorkspaceOnDevInstanceCreationFromSnapshot() throws Exception {
        final boolean isDev = true;
        final String hostProjectsFolder = "/tmp/projects";

        when(dockerNode.getProjectsFolder()).thenReturn(hostProjectsFolder);

        createInstanceFromSnapshot(isDev, WORKSPACE_ID);

        verify(dockerNode).bindWorkspace();
    }

    @Test
    public void shouldNotBindWorkspaceOnNonDevInstanceCreationFromRecipe() throws Exception {
        final boolean isDev = false;

        when(dockerNode.getProjectsFolder()).thenReturn("/tmp/projects");

        createInstanceFromRecipe(isDev, WORKSPACE_ID);

        verify(dockerNode, never()).bindWorkspace();
    }

    @Test
    public void shouldNotBindWorkspaceOnNonDevInstanceCreationFromSnapshot() throws Exception {
        final boolean isDev = false;

        when(dockerNode.getProjectsFolder()).thenReturn("/tmp/projects");

        createInstanceFromSnapshot(isDev, WORKSPACE_ID);

        verify(dockerNode, never()).bindWorkspace();
    }

    @Test
    public void shouldSetMemorySizeInContainersOnInstanceCreationFromRecipe() throws Exception {
        int memorySizeMB = 234;


        createInstanceFromRecipe(memorySizeMB);


        ArgumentCaptor<ContainerConfig> createContainerCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(createContainerCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));
        // docker accepts memory size in bytes
        assertEquals(createContainerCaptor.getValue().getHostConfig().getMemory(), memorySizeMB * 1024 * 1024);
    }

    @Test
    public void shouldSetMemorySizeInContainersOnInstanceCreationFromSnapshot() throws Exception {
        int memorySizeMB = 234;


        createInstanceFromSnapshot(memorySizeMB);


        ArgumentCaptor<ContainerConfig> createContainerCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(createContainerCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));
        // docker accepts memory size in bytes
        assertEquals(createContainerCaptor.getValue().getHostConfig().getMemory(), memorySizeMB * 1024 * 1024);
    }

    @Test
    public void shouldDisableSwapMemorySizeInContainersOnInstanceCreationFromRecipe() throws Exception {
        createInstanceFromRecipe();

        ArgumentCaptor<ContainerConfig> createContainerCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(createContainerCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));
        assertEquals(createContainerCaptor.getValue().getHostConfig().getMemorySwap(), -1);
    }

    @Test
    public void shouldDisableSwapMemorySizeInContainersOnInstanceCreationFromSnapshot() throws Exception {
        createInstanceFromSnapshot();

        ArgumentCaptor<ContainerConfig> createContainerCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(createContainerCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));
        assertEquals(createContainerCaptor.getValue().getHostConfig().getMemorySwap(), -1);
    }

    @Test
    public void shouldExposeCommonAndDevPortsToContainerOnDevInstanceCreationFromRecipe() throws Exception {
        List<String> expectedExposedPorts = new ArrayList<>();
        final Set<ServerConf> commonServers = new HashSet<>(asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                                   new ServerConfImpl("reference2", "8081", "ftp", null)));
        expectedExposedPorts.addAll(commonServers.stream()
                                                 .map(ServerConf::getPort)
                                                 .collect(Collectors.toList()));

        final Set<ServerConf> devServers = new HashSet<>(asList(new ServerConfImpl("reference3", "8082", "https", null),
                                                                new ServerConfImpl("reference4", "8083", "sftp", null)));
        expectedExposedPorts.addAll(devServers.stream()
                                              .map(ServerConf::getPort)
                                              .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            devServers,
                                                            commonServers,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldExposeOnlyCommonPortsToContainerOnNonDevInstanceCreationFromRecipe() throws Exception {
        List<String> expectedExposedPorts = new ArrayList<>();
        final Set<ServerConf> commonServers = new HashSet<>(asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                                   new ServerConfImpl("reference2", "8081", "ftp", null)));
        expectedExposedPorts.addAll(commonServers.stream()
                                                 .map(ServerConf::getPort)
                                                 .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            commonServers,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldExposeCommonAndDevPortsToContainerOnDevInstanceCreationFromSnapshot() throws Exception {
        List<String> expectedExposedPorts = new ArrayList<>();
        final Set<ServerConf> commonServers = new HashSet<>(asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                                   new ServerConfImpl("reference2", "8081", "ftp", null)));
        expectedExposedPorts.addAll(commonServers.stream()
                                                 .map(ServerConf::getPort)
                                                 .collect(Collectors.toList()));

        final Set<ServerConf> devServers = new HashSet<>(asList(new ServerConfImpl("reference3", "8082", "https", null),
                                                                new ServerConfImpl("reference4", "8083", "sftp", null)));
        expectedExposedPorts.addAll(devServers.stream()
                                              .map(ServerConf::getPort)
                                              .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            devServers,
                                                            commonServers,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldExposeOnlyCommonPortsToContainerOnNonDevInstanceCreationFromSnapshot() throws Exception {
        List<String> expectedExposedPorts = new ArrayList<>();
        final Set<ServerConf> commonServers = new HashSet<>(asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                                   new ServerConfImpl("reference2", "8081", "ftp", null)));
        expectedExposedPorts.addAll(commonServers.stream()
                                                 .map(ServerConf::getPort)
                                                 .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            commonServers,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldAddServersConfsPortsFromMachineConfigToExposedPortsOnNonDevInstanceCreationFromSnapshot() throws Exception {
        // given
        List<String> expectedExposedPorts = new ArrayList<>();
        final List<ServerConf> serversFromConf = asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                        new ServerConfImpl("reference2", "8081", "ftp", null));
        expectedExposedPorts.addAll(serversFromConf.stream()
                                                   .map(ServerConf::getPort)
                                                   .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;

        // when
        createInstanceFromSnapshot(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                          .setServers(serversFromConf)
                                                                                          .build())
                                                      .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldAddServersConfsPortsFromMachineConfigToExposedPortsOnNonDevInstanceCreationFromRecipe() throws Exception {
        // given
        List<String> expectedExposedPorts = new ArrayList<>();
        final List<ServerConf> serversFromConf = asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                        new ServerConfImpl("reference2", "8081", "ftp", null));
        expectedExposedPorts.addAll(serversFromConf.stream()
                                                   .map(ServerConf::getPort)
                                                   .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;

        // when
        createInstanceFromRecipe(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                        .setServers(serversFromConf)
                                                                                        .build())
                                                    .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldAddServersConfsPortsFromMachineConfigToExposedPortsOnDevInstanceCreationFromSnapshot() throws Exception {
        // given
        List<String> expectedExposedPorts = new ArrayList<>();
        final List<ServerConf> serversFromConf = asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                        new ServerConfImpl("reference2", "8081", "ftp", null));
        expectedExposedPorts.addAll(serversFromConf.stream()
                                                   .map(ServerConf::getPort)
                                                   .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;

        // when
        createInstanceFromSnapshot(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                          .setServers(serversFromConf)
                                                                                          .build())
                                                      .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldAddServersConfsPortsFromMachineConfigToExposedPortsOnDevInstanceCreationFromRecipe() throws Exception {
        // given
        List<String> expectedExposedPorts = new ArrayList<>();
        final List<ServerConf> serversFromConf = asList(new ServerConfImpl("reference1", "8080", "http", null),
                                                        new ServerConfImpl("reference2", "8081", "ftp", null));
        expectedExposedPorts.addAll(serversFromConf.stream()
                                                   .map(ServerConf::getPort)
                                                   .collect(Collectors.toList()));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;

        // when
        createInstanceFromRecipe(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                        .setServers(serversFromConf)
                                                                                        .build())
                                                    .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());

        assertTrue(new ArrayList<>(argumentCaptor.getValue().getExposedPorts().keySet()).containsAll(expectedExposedPorts));
    }

    @Test
    public void shouldBindProjectsFSVolumeToContainerOnDevInstanceCreationFromRecipe() throws Exception {
        final String expectedHostPathOfProjects = "/tmp/projects";
        String[] expectedVolumes = new String[] {expectedHostPathOfProjects + ":/projects:Z"};

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(workspaceFolderPathProvider.getPath(anyString())).thenReturn(expectedHostPathOfProjects);

        final boolean isDev = true;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        assertEquals(argumentCaptor.getValue().getHostConfig().getBinds(), expectedVolumes);
    }

    @Test
    public void shouldBindProjectsFSVolumeToContainerOnDevInstanceCreationFromSnapshot() throws Exception {
        final String expectedHostPathOfProjects = "/tmp/projects";
        final String[] expectedVolumes = new String[] {expectedHostPathOfProjects + ":/projects:Z"};

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(workspaceFolderPathProvider.getPath(anyString())).thenReturn(expectedHostPathOfProjects);

        final boolean isDev = true;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        assertEquals(argumentCaptor.getValue().getHostConfig().getBinds(), expectedVolumes);
    }

    @Test
    public void shouldNotBindProjectsFSVolumeToContainerOnNonDevInstanceCreationFromRecipe() throws Exception {
        String[] expectedVolumes = new String[0];

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn("/tmp/projects");

        final boolean isDev = false;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        assertEquals(argumentCaptor.getValue().getHostConfig().getBinds(), expectedVolumes);
    }

    @Test
    public void shouldNotBindProjectsFSVolumeToContainerOnNonDevInstanceCreationFromSnapshot() throws Exception {
        String[] expectedVolumes = new String[0];

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn("/tmp/projects");

        final boolean isDev = false;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        assertEquals(argumentCaptor.getValue().getHostConfig().getBinds(), expectedVolumes);
    }

    @Test
    public void shouldBindCommonAndDevVolumesToContainerOnDevInstanceCreationFromRecipe() throws Exception {
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        final ArrayList<String> expectedVolumes = new ArrayList<>();
        expectedVolumes.addAll(devVolumes);
        expectedVolumes.addAll(commonVolumes);
        expectedVolumes.add(expectedHostPathOfProjects + ":/projects:Z");

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(workspaceFolderPathProvider.getPath(anyString())).thenReturn(expectedHostPathOfProjects);
        final boolean isDev = true;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] actualBinds = argumentCaptor.getValue().getHostConfig().getBinds();
        assertEquals(actualBinds.length, expectedVolumes.size());
        assertEquals(new HashSet<>(asList(actualBinds)), new HashSet<>(expectedVolumes));
    }

    @Test
    public void shouldBindCommonAndDevVolumesToContainerOnDevInstanceCreationFromSnapshot() throws Exception {
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        final ArrayList<String> expectedVolumes = new ArrayList<>();
        expectedVolumes.addAll(devVolumes);
        expectedVolumes.addAll(commonVolumes);
        expectedVolumes.add(expectedHostPathOfProjects + ":/projects:Z");

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(workspaceFolderPathProvider.getPath(anyString())).thenReturn(expectedHostPathOfProjects);

        final boolean isDev = true;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] actualBinds = argumentCaptor.getValue().getHostConfig().getBinds();
        assertEquals(actualBinds.length, expectedVolumes.size());
        assertEquals(new HashSet<>(asList(actualBinds)), new HashSet<>(expectedVolumes));
    }

    @Test
    public void shouldBindCommonVolumesOnlyToContainerOnNonDevInstanceCreationFromRecipe() throws Exception {
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        final ArrayList<String> expectedVolumes = new ArrayList<>();
        expectedVolumes.addAll(commonVolumes);

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn(expectedHostPathOfProjects);

        final boolean isDev = false;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] actualBinds = argumentCaptor.getValue().getHostConfig().getBinds();
        assertEquals(actualBinds.length, expectedVolumes.size());
        assertEquals(new HashSet<>(asList(actualBinds)), new HashSet<>(expectedVolumes));
    }

    @Test
    public void shouldAddExtraHostOnDevInstanceCreationFromRecipe() throws Exception {
        //given
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            "dev.box.com:192.168.0.1",
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn(expectedHostPathOfProjects);

        final boolean isDev = true;

        //when
        createInstanceFromRecipe(isDev);

        //then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] extraHosts = argumentCaptor.getValue().getHostConfig().getExtraHosts();
        assertEquals(extraHosts.length, 2);
        assertEquals(extraHosts[0], "dev.box.com:192.168.0.1");
    }

    @Test
    public void shouldAddExtraHostOnDevInstanceCreationFromSnapshot() throws Exception {
        //given
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            "dev.box.com:192.168.0.1,codenvy.com.com:185",
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn(expectedHostPathOfProjects);
        final boolean isDev = true;

        //when
        createInstanceFromSnapshot(isDev);
        //then

        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] extraHosts = argumentCaptor.getValue().getHostConfig().getExtraHosts();
        assertEquals(extraHosts.length, 3);
        assertEquals(extraHosts[0], "dev.box.com:192.168.0.1");
        assertEquals(extraHosts[1], "codenvy.com.com:185");
    }

    @Test
    public void shouldAddExtraHostOnNonDevInstanceCreationFromRecipe() throws Exception {
        //given
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            "dev.box.com:192.168.0.1",
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn(expectedHostPathOfProjects);

        final boolean isDev = false;

        //when
        createInstanceFromRecipe(isDev);

        //then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] extraHosts = argumentCaptor.getValue().getHostConfig().getExtraHosts();
        assertEquals(extraHosts.length, 2);
        assertEquals(extraHosts[0], "dev.box.com:192.168.0.1");
    }

    @Test
    public void shouldAddExtraHostOnNonDevInstanceCreationFromSnapshot() throws Exception {
        //given
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            "dev.box.com:192.168.0.1,codenvy.com.com:185",
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn(expectedHostPathOfProjects);
        final boolean isDev = false;

        //when
        createInstanceFromSnapshot(isDev);
        //then

        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] extraHosts = argumentCaptor.getValue().getHostConfig().getExtraHosts();
        assertEquals(extraHosts.length, 3);
        assertEquals(extraHosts[0], "dev.box.com:192.168.0.1");
        assertEquals(extraHosts[1], "codenvy.com.com:185");
    }

    @Test
    public void shouldBindCommonVolumesOnlyToContainerOnNonDevInstanceCreationFromSnapshot() throws Exception {
        final String expectedHostPathOfProjects = "/tmp/projects";
        Set<String> devVolumes = new HashSet<>(asList("/etc:/tmp/etc:ro", "/some/thing:/home/some/thing"));
        Set<String> commonVolumes = new HashSet<>(asList("/some/thing/else:/home/some/thing/else", "/other/path:/home/other/path"));

        final ArrayList<String> expectedVolumes = new ArrayList<>();
        expectedVolumes.addAll(commonVolumes);

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            devVolumes,
                                                            commonVolumes,
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        when(dockerNode.getProjectsFolder()).thenReturn(expectedHostPathOfProjects);

        final boolean isDev = false;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        verify(dockerConnector).startContainer(anyString(), eq(null));

        final String[] actualBinds = argumentCaptor.getValue().getHostConfig().getBinds();
        assertEquals(actualBinds.length, expectedVolumes.size());
        assertEquals(new HashSet<>(asList(actualBinds)), new HashSet<>(expectedVolumes));
    }

    @Test
    public void shouldAddWorkspaceIdEnvVariableOnDevInstanceCreationFromRecipe() throws Exception {
        String wsId = "myWs";
        createInstanceFromRecipe(true, wsId);
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(asList(argumentCaptor.getValue().getEnv())
                           .contains(DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + wsId),
                   "Workspace Id variable is missing. Required " + DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + wsId +
                   ". Found " + Arrays.toString(argumentCaptor.getValue().getEnv()));
    }

    @Test
    public void shouldAddWorkspaceIdEnvVariableOnDevInstanceCreationFromSnapshot() throws Exception {
        String wsId = "myWs";
        createInstanceFromSnapshot(true, wsId);
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(asList(argumentCaptor.getValue().getEnv())
                           .contains(DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + wsId),
                   "Workspace Id variable is missing. Required " + DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + wsId +
                   ". Found " + Arrays.toString(argumentCaptor.getValue().getEnv()));
    }

    @Test
    public void shouldNotAddWorkspaceIdEnvVariableOnNonDevInstanceCreationFromRecipe() throws Exception {
        String wsId = "myWs";
        createInstanceFromRecipe(false, wsId);
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertFalse(asList(argumentCaptor.getValue().getEnv())
                            .contains(DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + wsId),
                    "Non dev machine should not contains " + DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID);
    }

    @Test
    public void shouldNotAddWorkspaceIdEnvVariableOnNonDevInstanceCreationFromSnapshot() throws Exception {
        String wsId = "myWs";
        createInstanceFromSnapshot(false, wsId);
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertFalse(asList(argumentCaptor.getValue().getEnv())
                            .contains(DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + wsId),
                    "Non dev machine should not contains " + DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID);
    }

    /**
     * E.g from https://github.com/boot2docker/boot2docker/blob/master/README.md#virtualbox-guest-additions
     *
     * Users should be /Users
     * /Users should be /Users
     * c/Users should be /c/Users
     * /c/Users should be /c/Users
     * c:/Users should be /c/Users
     */
    @Test
    public void shouldEscapePathForWindowsHost() {
        assertEquals(dockerInstanceProvider.escapePath("Users"), "/Users");
        assertEquals(dockerInstanceProvider.escapePath("/Users"), "/Users");
        assertEquals(dockerInstanceProvider.escapePath("c/Users"), "/c/Users");
        assertEquals(dockerInstanceProvider.escapePath("/c/Users"), "/c/Users");
        assertEquals(dockerInstanceProvider.escapePath("c:/Users"), "/c/Users");
        assertEquals(dockerInstanceProvider.escapePath("C:/Users"), "/c/Users");

        assertEquals(dockerInstanceProvider.escapePath("C:/Users/path/dir/from/host:/name/of/dir/in/container"),
                     "/c/Users/path/dir/from/host:/name/of/dir/in/container");
    }

    @Test
    public void shouldAddCommonAndDevEnvVariablesToContainerOnDevInstanceCreationFromRecipe() throws Exception {
        Set<String> commonEnv = new HashSet<>(asList("ENV_VAR1=123", "ENV_VAR2=234"));
        Set<String> devEnv = new HashSet<>(asList("DEV_ENV_VAR1=345", "DEV_ENV_VAR2=456", "DEV_ENV_VAR3=567"));
        Set<String> expectedEnv = new HashSet<>();
        expectedEnv.addAll(commonEnv);
        expectedEnv.addAll(devEnv);
        expectedEnv.add(DockerInstanceRuntimeInfo.USER_TOKEN + "=" + USER_TOKEN);
        expectedEnv.add(DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + WORKSPACE_ID);

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            devEnv,
                                                            commonEnv,
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(new HashSet<>(asList(argumentCaptor.getValue().getEnv())).containsAll(expectedEnv));
    }

    @Test
    public void shouldNotAddDevEnvToCommonEnvVariablesToContainerOnNonDevInstanceCreationFromRecipe() throws Exception {
        Set<String> commonEnv = new HashSet<>(asList("ENV_VAR1=123", "ENV_VAR2=234"));
        Set<String> devEnv = new HashSet<>(asList("DEV_ENV_VAR1=345", "DEV_ENV_VAR2=456", "DEV_ENV_VAR3=567"));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            devEnv,
                                                            commonEnv,
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;


        createInstanceFromRecipe(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(new HashSet<>(asList(argumentCaptor.getValue().getEnv())).containsAll(commonEnv));
    }

    @Test
    public void shouldAddCommonAndDevEnvVariablesToContainerOnDevInstanceCreationFromSnapshot() throws Exception {
        Set<String> commonEnv = new HashSet<>(asList("ENV_VAR1=123", "ENV_VAR2=234"));
        Set<String> devEnv = new HashSet<>(asList("DEV_ENV_VAR1=345", "DEV_ENV_VAR2=456", "DEV_ENV_VAR3=567"));
        Set<String> expectedEnv = new HashSet<>();
        expectedEnv.addAll(commonEnv);
        expectedEnv.addAll(devEnv);
        expectedEnv.add(DockerInstanceRuntimeInfo.USER_TOKEN + "=" + USER_TOKEN);
        expectedEnv.add(DockerInstanceRuntimeInfo.CHE_WORKSPACE_ID + "=" + WORKSPACE_ID);

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            devEnv,
                                                            commonEnv,
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(new HashSet<>(asList(argumentCaptor.getValue().getEnv())).containsAll(expectedEnv));
    }

    @Test
    public void shouldNotAddDevEnvToCommonEnvVariablesToContainerOnNonDevInstanceCreationFromSnapshot() throws Exception {
        Set<String> commonEnv = new HashSet<>(asList("ENV_VAR1=123", "ENV_VAR2=234"));
        Set<String> devEnv = new HashSet<>(asList("DEV_ENV_VAR1=345", "DEV_ENV_VAR2=456", "DEV_ENV_VAR3=567"));

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            devEnv,
                                                            commonEnv,
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;


        createInstanceFromSnapshot(isDev);


        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(new HashSet<>(asList(argumentCaptor.getValue().getEnv())).containsAll(commonEnv));
    }

    @Test
    public void shouldAddEnvVarsFromMachineConfigToContainerOnNonDevInstanceCreationFromSnapshot() throws Exception {
        // given
        Map<String, String> envVarsFromConfig = new HashMap<>();
        envVarsFromConfig.put("ENV_VAR1", "123");
        envVarsFromConfig.put("ENV_VAR2", "234");

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;

        // when
        createInstanceFromSnapshot(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                          .setEnvVariables(envVarsFromConfig)
                                                                                          .build())
                                                      .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(asList(argumentCaptor.getValue().getEnv()).containsAll(envVarsFromConfig.entrySet()
                                                                                           .stream()
                                                                                           .map(entry -> entry.getKey() +
                                                                                                         "=" +
                                                                                                         entry.getValue())
                                                                                           .collect(Collectors.toList())));
    }

    @Test
    public void shouldAddEnvVarsFromMachineConfigToContainerOnDevInstanceCreationFromSnapshot() throws Exception {
        // given
        Map<String, String> envVarsFromConfig = new HashMap<>();
        envVarsFromConfig.put("ENV_VAR1", "123");
        envVarsFromConfig.put("ENV_VAR2", "234");

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;

        // when
        createInstanceFromSnapshot(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                          .setEnvVariables(envVarsFromConfig)
                                                                                          .build())
                                                      .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(asList(argumentCaptor.getValue().getEnv()).containsAll(envVarsFromConfig.entrySet()
                                                                                           .stream()
                                                                                           .map(entry -> entry.getKey() +
                                                                                                         "=" +
                                                                                                         entry.getValue())
                                                                                           .collect(Collectors.toList())));
    }

    @Test
    public void shouldAddEnvVarsFromMachineConfigToContainerOnNonDevInstanceCreationFromRecipe() throws Exception {
        // given
        Map<String, String> envVarsFromConfig = new HashMap<>();
        envVarsFromConfig.put("ENV_VAR1", "123");
        envVarsFromConfig.put("ENV_VAR2", "234");

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = false;

        // when
        createInstanceFromRecipe(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                        .setEnvVariables(envVarsFromConfig)
                                                                                        .build())
                                                    .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(asList(argumentCaptor.getValue().getEnv()).containsAll(envVarsFromConfig.entrySet()
                                                                                           .stream()
                                                                                           .map(entry -> entry.getKey() +
                                                                                                         "=" +
                                                                                                         entry.getValue())
                                                                                           .collect(Collectors.toList())));
    }

    @Test
    public void shouldAddEnvVarsFromMachineConfigToContainerOnDevInstanceCreationFromRecipe() throws Exception {
        // given
        Map<String, String> envVarsFromConfig = new HashMap<>();
        envVarsFromConfig.put("ENV_VAR1", "123");
        envVarsFromConfig.put("ENV_VAR2", "234");

        dockerInstanceProvider = new DockerInstanceProvider(dockerConnector,
                                                            dockerConnectorConfiguration,
                                                            dockerMachineFactory,
                                                            dockerInstanceStopDetector,
                                                            containerNameGenerator,
                                                            recipeRetriever,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            null,
                                                            workspaceFolderPathProvider,
                                                            PROJECT_FOLDER_PATH,
                                                            false,
                                                            false,
                                                            Collections.emptySet(),
                                                            Collections.emptySet(),
                                                            SNAPSHOT_USE_REGISTRY);

        final boolean isDev = true;

        // when
        createInstanceFromRecipe(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                        .setEnvVariables(envVarsFromConfig)
                                                                                        .build())
                                                    .build());

        // then
        ArgumentCaptor<ContainerConfig> argumentCaptor = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerConnector).createContainer(argumentCaptor.capture(), anyString());
        assertTrue(asList(argumentCaptor.getValue().getEnv()).containsAll(envVarsFromConfig.entrySet()
                                                                                           .stream()
                                                                                           .map(entry -> entry.getKey() +
                                                                                                         "=" +
                                                                                                         entry.getValue())
                                                                                           .collect(Collectors.toList())));
    }

    private void createInstanceFromRecipe() throws Exception {
        createInstanceFromRecipe(getMachineBuilder().build());
    }

    private void createInstanceFromRecipe(boolean isDev) throws Exception {
        createInstanceFromRecipe(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                        .build())
                                                    .build());
    }

    private void createInstanceFromRecipe(boolean isDev, String workspaceId) throws Exception {
        createInstanceFromRecipe(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                        .build())
                                                    .setWorkspaceId(workspaceId)
                                                    .build());
    }

    private void createInstanceFromRecipe(int memorySizeInMB) throws Exception {
        createInstanceFromRecipe(getMachineBuilder().setConfig(getMachineConfigBuilder().setLimits(new LimitsImpl(memorySizeInMB))
                                                                                        .build())
                                                    .build());
    }

    private void createInstanceFromSnapshot(String repo, String tag, String registry) throws NotFoundException, MachineException {
        createInstanceFromSnapshot(getMachineBuilder().build(), new DockerMachineSource(repo).withTag(tag).withRegistry(registry).withDigest("digest"));
    }

    private void createInstanceFromRecipe(Machine machine) throws Exception {
        dockerInstanceProvider.createInstance(machine,
                                              LineConsumer.DEV_NULL);
    }

    private void createInstanceFromSnapshot() throws NotFoundException, MachineException {
        createInstanceFromSnapshot(getMachineBuilder().build());
    }

    private void createInstanceFromSnapshot(int memorySizeInMB) throws NotFoundException, MachineException {
        createInstanceFromSnapshot(getMachineBuilder().setConfig(getMachineConfigBuilder().setLimits(new LimitsImpl(memorySizeInMB))
                                                                                          .build())
                                                      .build());
    }

    private void createInstanceFromSnapshot(boolean isDev) throws NotFoundException, MachineException {
        createInstanceFromSnapshot(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                          .build())
                                                      .build());
    }

    private void createInstanceFromSnapshot(boolean isDev, String workspaceId) throws NotFoundException, MachineException {
        createInstanceFromSnapshot(getMachineBuilder().setConfig(getMachineConfigBuilder().setDev(isDev)
                                                                                          .build())
                                                      .setWorkspaceId(workspaceId)
                                                      .build());
    }

    private void createInstanceFromSnapshot(MachineImpl machine) throws NotFoundException, MachineException {
        DockerMachineSource machineSource = new DockerMachineSource("repo").withRegistry("registry").withDigest("digest");
        machine.getConfig().setSource(machineSource);
        dockerInstanceProvider.createInstance(machine,
                                              LineConsumer.DEV_NULL);
    }

    private void createInstanceFromSnapshot(MachineImpl machine, DockerMachineSource dockerMachineSource) throws NotFoundException,
                                                                                                             MachineException {

        machine.getConfig().setSource(dockerMachineSource);
        dockerInstanceProvider.createInstance(machine,
                                              LineConsumer.DEV_NULL);
    }

    private MachineImpl.MachineImplBuilder getMachineBuilder() {
        return MachineImpl.builder().fromMachine(new MachineImpl(getMachineConfigBuilder().build(),
                                                                 MACHINE_ID,
                                                                 WORKSPACE_ID,
                                                                 "envName",
                                                                 "userId",
                                                                 MachineStatus.CREATING,
                                                                 null));
    }

    private DockerInstanceProvider getDockerInstanceProvider(boolean snapshotUseRegistry) throws Exception {
        return spy(new DockerInstanceProvider(dockerConnector,
                                              dockerConnectorConfiguration,
                                              dockerMachineFactory,
                                              dockerInstanceStopDetector,
                                              containerNameGenerator,
                                              recipeRetriever,
                                              Collections.emptySet(),
                                              Collections.emptySet(),
                                              Collections.emptySet(),
                                              Collections.emptySet(),
                                              null,
                                              workspaceFolderPathProvider,
                                              PROJECT_FOLDER_PATH,
                                              false,
                                              false,
                                              Collections.emptySet(),
                                              Collections.emptySet(),
                                              snapshotUseRegistry));
    }

    private MachineConfigImpl.MachineConfigImplBuilder getMachineConfigBuilder() {
        return MachineConfigImpl.builder().fromConfig(new MachineConfigImpl(false,
                                                                            MACHINE_NAME,
                                                                            "machineType",
                                                                            new MachineSourceImpl(DOCKER_FILE_TYPE).setContent("FROM codenvy"),
                                                                            new LimitsImpl(MEMORY_LIMIT_MB),
                                                                            asList(new ServerConfImpl("ref1",
                                                                                                      "8080",
                                                                                                      "https",
                                                                                                      null),
                                                                                   new ServerConfImpl("ref2",
                                                                                                      "9090/udp",
                                                                                                      "someprotocol",
                                                                                                      null)),
                                                                            Collections.singletonMap("key1", "value1")));
    }
}
