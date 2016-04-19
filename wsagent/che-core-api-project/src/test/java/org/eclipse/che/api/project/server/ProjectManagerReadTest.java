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
package org.eclipse.che.api.project.server;

import org.eclipse.che.api.core.model.project.ProjectConfig;
import org.eclipse.che.api.core.model.project.type.Value;
import org.eclipse.che.api.project.server.handlers.ProjectHandlerRegistry;
import org.eclipse.che.api.project.server.type.BaseProjectType;
import org.eclipse.che.api.project.server.type.ProjectTypeRegistry;
import org.eclipse.che.api.project.server.type.ProjectTypeResolution;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.dto.server.DtoFactory;
import org.junit.Before;
import org.junit.Test;
import org.testng.Assert;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;
import static org.testng.Assert.assertTrue;


/**
 * @author gazarenkov
 */
public class ProjectManagerReadTest extends WsAgentTestBase {

    final String value = NameGenerator.generate("value", 10);



    @Before
    public void setUp() throws Exception {

        super.setUp();


        new File(root, "/fromFolder").mkdir();
        new File(root, "/normal").mkdir();
        new File(root, "/normal/module").mkdir();

        File prjWithAttr = new File(root, "/" + PROJECT_TYPE_WITH_ATTRIBUTE);
        prjWithAttr.mkdir();
        File file = new File(root, "/" + PROJECT_TYPE_WITH_ATTRIBUTE + "/.conf");
        file.createNewFile();

        Files.write(file.toPath(),value.getBytes());

        List<ProjectConfigDto> projects = new ArrayList<>();
        projects.add(DtoFactory.newDto(ProjectConfigDto.class)
                               .withPath("/normal")
                               .withName("project1Name")
                               .withType("primary1"));

        projects.add(DtoFactory.newDto(ProjectConfigDto.class)
                               .withPath("/fromConfig")
                               .withName("")
                               .withType("primary1"));


        projects.add(DtoFactory.newDto(ProjectConfigDto.class)
                              .withPath("/normal/module")
                              .withName("project1Name")
                              .withType("primary1"));


        projects.add(DtoFactory.newDto(ProjectConfigDto.class)
                                .withPath("/" + PROJECT_TYPE_WITH_ATTRIBUTE)
                                .withName("project1Name")
                                .withType(PROJECT_TYPE_WITH_ATTRIBUTE));


        workspaceHolder = new TestWorkspaceHolder(projects);
        ProjectTypeRegistry projectTypeRegistry = new ProjectTypeRegistry(new HashSet<>());
        projectTypeRegistry.registerProjectType(new PT1());
        projectTypeRegistry.registerProjectType(new PT3());
        projectTypeRegistry.registerProjectType(new ProjectTypeWitAttribute());

        ProjectHandlerRegistry projectHandlerRegistry = new ProjectHandlerRegistry(new HashSet<>());

        projectRegistry = new ProjectRegistry(workspaceHolder, vfsProvider, projectTypeRegistry, projectHandlerRegistry);
        projectRegistry.initProjects();

        pm = new ProjectManager(vfsProvider, null, projectTypeRegistry, projectRegistry, projectHandlerRegistry,
                                null, fileWatcherNotificationHandler, fileTreeWatcher);
        pm.initWatcher();
    }


    @Test
    public void testInit() throws Exception {

        assertEquals(4, projectRegistry.getProjects().size());
        assertEquals(0, projectRegistry.getProject("/normal").getProblems().size());
        assertEquals(1, projectRegistry.getProject("/fromConfig").getProblems().size());
        assertEquals(1, projectRegistry.getProject("/fromFolder").getProblems().size());

    }


    @Test
    public void testNormalProject() throws Exception {

        assertEquals(4, pm.getProjects().size());
        assertEquals("/normal", pm.getProject("/normal").getPath());
        assertEquals("project1Name", pm.getProject("/normal").getName());
        assertEquals(0, pm.getProject("/normal").getProblems().size());

        for(VirtualFileEntry entry : pm.getProjectsRoot().getChildren()) {
            System.out.println(">>>> "+entry.getPath()+" "+entry.getProject());
        }

        VirtualFileEntry entry = pm.getProjectsRoot().getChild("normal");
        assertTrue(entry.isProject());

    }

    @Test
    public void testProjectFromFolder() throws Exception {

        assertEquals("/fromFolder", pm.getProject("/fromFolder").getPath());
        assertEquals("fromFolder", pm.getProject("/fromFolder").getName());
        assertEquals(1, pm.getProject("/fromFolder").getProblems().size());
        assertEquals(BaseProjectType.ID, pm.getProject("/fromFolder").getProjectType().getId());
        assertEquals(11, pm.getProject("/fromFolder").getProblems().get(0).code);
    }

    @Test
    public void testProjectFromConfig() throws Exception {

        assertEquals("/fromConfig", pm.getProject("/fromConfig").getPath());
        assertEquals(1, pm.getProject("/fromConfig").getProblems().size());
        assertEquals("primary1", pm.getProject("/fromConfig").getProjectType().getId());
        assertEquals(10, pm.getProject("/fromConfig").getProblems().get(0).code);
    }

    @Test
    public void testInnerProject() throws Exception {

        String path = "/normal/module";
        assertEquals(0, pm.getProject(path).getProblems().size());
        assertEquals("primary1", pm.getProject(path).getProjectType().getId());


    }

    @Test
    public void testParentProject() throws Exception {

//        try {
        assertEquals("/normal", projectRegistry.getParentProject("/normal").getPath());
//            fail("NotFoundException expected");
//        } catch (NotFoundException e) {}

        assertEquals("/normal", projectRegistry.getParentProject("/normal/some/path").getPath());
        assertEquals("/normal/module", projectRegistry.getParentProject("/normal/module/some/path").getPath());

//        try {
        assertNull(projectRegistry.getParentProject("/some/path"));
//            fail("NotFoundException expected");
//        } catch (NotFoundException e) {}


    }

    @Test
    public void testSerializeProject() throws Exception {
        ProjectConfig config = DtoConverter.asDto(pm.getProject("/fromConfig"));

        assertEquals("/fromConfig", config.getPath());
        assertEquals("primary1", config.getType());

    }


    @Test
    public void testDoNotReturnNotInitializedAttribute() throws Exception {

        // SPEC:
        // Not initialized attributes should not be returned

        assertEquals(1, projectRegistry.getProject("/normal").getAttributes().size());

    }

    @Test
    public void testEstimateProject() throws Exception {
        //getting project and check initial attribute
        String projectPath = "/" + PROJECT_TYPE_WITH_ATTRIBUTE;
        RegisteredProject project = pm.getProject(projectPath);
        assertNotNull(project);
        assertEquals(PROJECT_TYPE_WITH_ATTRIBUTE, project.getType());
        Map<String, List<String>> attributes = project.getAttributes();
        assertNotNull(attributes);
        assertEquals(1, attributes.size());
        assertTrue(attributes.containsKey(PROVIDED_ATTRIBUTE));
        assertEquals(value, attributes.get(PROVIDED_ATTRIBUTE).get(0));


        //update content file
        //value provider should read new value during estimate project
        File file = new File(root, "/" + PROJECT_TYPE_WITH_ATTRIBUTE + "/.conf");
        String newValue = NameGenerator.generate("new value", 12);
        Files.write(file.toPath(), newValue.getBytes());
        ProjectTypeResolution projectTypeResolution = pm.estimateProject(projectPath, PROJECT_TYPE_WITH_ATTRIBUTE);
        assertNotNull(projectTypeResolution);
        Map<String, Value> providedAttributes = projectTypeResolution.getProvidedAttributes();
        assertNotNull(providedAttributes);
        Value providedValue = providedAttributes.get(PROVIDED_ATTRIBUTE);
        assertNotNull(providedValue);
        assertFalse(providedValue.isEmpty());
        assertEquals(newValue, providedValue.getString());


        //create new project config and update project with it
        Map<String, List<String>> map = new HashMap<>();
        for (String key : attributes.keySet()) {
            if (providedAttributes.containsKey(key)) {
                map.put(key, providedAttributes.get(key).getList());
            } else {
                map.put(key, attributes.get(key));
            }
        }

        ProjectConfig projectConfig = new NewProjectConfig(project.getPath(), project.getType(), project.getMixins(),
                project.getName(), project.getDescription(),map, project.getSource());
        pm.updateProject(projectConfig);

        //re-get project again and check on new attribute
        RegisteredProject reFreshedProject = pm.getProject(projectPath);
        assertNotNull(reFreshedProject);
        assertEquals(PROJECT_TYPE_WITH_ATTRIBUTE, reFreshedProject.getType());
        Map<String, List<String>> refreshedAttributes = reFreshedProject.getAttributes();
        assertNotNull(refreshedAttributes);
        assertEquals(1, refreshedAttributes.size());
        assertTrue(refreshedAttributes.containsKey(PROVIDED_ATTRIBUTE));
        assertEquals(newValue, refreshedAttributes.get(PROVIDED_ATTRIBUTE).get(0));
    }

    @Test
    public void testResolveSources() throws Exception {

    }


    @Test
    public void testIfConstantAttrIsAccessible() throws Exception {

        assertEquals("my constant", pm.getProject("/normal").getAttributeEntries().get("const1").getString());

    }



}
