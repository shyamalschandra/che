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

import com.google.common.base.MoreObjects;

import org.eclipse.che.api.core.model.machine.MachineSource;
import org.eclipse.che.api.machine.server.model.impl.MachineSourceImpl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.che.plugin.docker.machine.DockerInstanceProvider.DOCKER_IMAGE_TYPE;

/**
 * Set of helper methods that identifies docker image properties
 *
 * @author Sergii Kabashnyuk
 * @author Florent Benoit
 */
public class DockerMachineSource extends MachineSourceImpl {

    /**
     * Regexp used to parse a location of Docker image.
     * it's based on optional registry followed by repository name followed by a digest or a tag
     * Examples are available in the test.
     */
    public static final Pattern IMAGE_PATTERN = Pattern.compile("^((?<registry>[^/]++)(?:\\/))?(?<repository>.*?)((?:\\:)(?<tag>.*?))?((?:@)(?<digest>.*))?$");

    /**
     * Optional registry (like docker-registry.company.com:5000)
     */
    private String registry;

    /**
     * mandatory repository name (like codenvy/ubuntu_jdk8)
     */
    private String repository;

    /**
     * optional tag of the image (like latest)
     */
    private String tag;

    /**
     * optional digest of the image (like sha256@1234)
     */
    private String digest;


    /**
     * Build a dedicated docker image source based on a given machine source object.
     * @param machineSource the machine source used to parse data.
     */
    public DockerMachineSource(MachineSource machineSource) {
        super();

        // check type
        if (!DOCKER_IMAGE_TYPE.equals(machineSource.getType())) {
            throw new IllegalArgumentException("Docker machine source can only be built with '" + DOCKER_IMAGE_TYPE + "' type");
        }
        setType(DOCKER_IMAGE_TYPE);

        // parse either content or location
        String expression = MoreObjects.firstNonNull(machineSource.getContent(), machineSource.getLocation());
        Matcher matcher = IMAGE_PATTERN.matcher(expression);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Try to restore machine source with an invalid location/content. It is not in the expected format");
        }

        // populate
        this.registry = matcher.group("registry");
        this.repository = matcher.group("repository");
        this.tag = matcher.group("tag");
        this.digest = matcher.group("digest");

    }


    /**
     * Build image source based on given arguments
     * @param repository as for example codenvy/ubuntu_jdk8
     */
    public DockerMachineSource(String repository) {
        super();
        this.repository = repository;
        setType(DOCKER_IMAGE_TYPE);
    }

    /**
     * Defines optional tag attribute
     * @param tag as for example latest
     * @return current instance
     */
    public DockerMachineSource setTag(String tag) {
        this.tag = tag;
        return this;
    }

    /**
     * Defines optional registry attribute
     * @param registry as for example docker-registry.company.com:5000
     * @return current instance
     */
    public DockerMachineSource setRegistry(String registry) {
        this.registry = registry;
        return this;
    }


    /**
     * Defines optional digest attribute
     * @param digest as for example sha256@1234
     * @return current instance
     */
    public DockerMachineSource setDigest(String digest) {
        this.digest = digest;
        return this;
    }


    /**
     * @return mandatory repository
     */
    public String getRepository() {
        return this.repository;
    }

    /**
     * @return optional tag
     */
    public String getTag() {
        return this.tag;
    }

    /**
     * @return optional registry
     */
    public String getRegistry() {
        return this.registry;
    }

    /**
     * @return optional digest
     */
    public String getDigest() {
        return this.digest;
    }

    /**
     * Returns location of this docker image, including all data that are required to reconstruct a new docker machine source.
     */
    public String getLocation() {
        return getLocation(true);
    }

    /**
     * Returns full name of docker image.
     * <p>
     * It consists of registry, userspace, repository name, tag, digest.
     * E.g. docker-registry.company.com:5000/userspace1/my-repository:some-tag
     * E.g. docker-registry.company.com:5000/userspace1/my-repository@some-digest
     * @param includeDigest if digest needs to be included or not
     */
    public String getLocation(boolean includeDigest) {
        final StringBuilder fullRepoId = new StringBuilder();

        // optional registry is followed by /
        if (getRegistry() != null) {
            fullRepoId.append(getRegistry()).append('/');
        }

        // repository
        fullRepoId.append(getRepository());

        // optional tag (: prefix)
        if (getTag() != null) {
            fullRepoId.append(':').append(getTag());
        }

        // optional digest (@ prefix)
        if (includeDigest && getDigest() != null) {
            fullRepoId.append('@').append(getDigest());
        }
        return fullRepoId.toString();
    }

    /**
     * Returns full name of docker image (without digest)
     */
    public String getFullName() {
        return getLocation(false);
    }

}
