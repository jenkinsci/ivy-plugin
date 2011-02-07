/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi, Timothy Bingaman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.ivy;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;

/**
 * Serializable representation of the key information obtained from an ivy
 * descriptor file.
 *
 * <p>
 * This is used for the master to introspect the ivy descriptor file, which is
 * only available as {@link ModuleDescriptor} object on slaves.
 *
 * @author Timothy Bingaman
 */
final class IvyModuleInfo implements Serializable {

    public final ModuleName name;

    /**
     * This is a human readable name of the Ivy module. Not necessarily unique
     * or file system safe.
     *
     * @see ModuleRevisionId#getName()
     */
    public final String displayName;

    /**
     * Relative path from the workspace to the ivy descriptor file for this
     * module.
     *
     * Strings like "ivy.xml" (if the ivy.xml file is checked out directly in
     * the workspace), "abc/ivy.xml", "foo/bar/zot/ivy.xml".
     */
    public final String relativePathToDescriptor;

    /**
     * Revision number taken from ivy descriptor file.
     *
     * @see ModuleRevisionId#getRevision()
     */
    public final String revision;

    /**
     * Ivy branch taken from ivy descriptor file.
     *
     * @see ModuleRevisionId#getBranch()
     */
    public final String branch;

    /**
     * Dependency of this project.
     */
    public final Set<ModuleDependency> dependencies = new LinkedHashSet<ModuleDependency>();

    public IvyModuleInfo(ModuleDescriptor module, String relativePathToDescriptor) {
        this.name = new ModuleName(module);
        ModuleRevisionId mrid = module.getModuleRevisionId();
        this.revision = (mrid.getRevision() == null || mrid.getRevision().startsWith("working@") || mrid.getRevision().contains("${")) ? ModuleDependency.UNKNOWN
                : mrid.getRevision();
        this.branch = (mrid.getBranch() == null || mrid.getBranch().contains("${")) ? ModuleDependency.UNKNOWN : mrid.getBranch();
        this.displayName = mrid.getName();
        this.relativePathToDescriptor = relativePathToDescriptor;

        for (DependencyDescriptor dep : module.getDependencies())
            dependencies.add(new ModuleDependency(dep));
    }

    private static final long serialVersionUID = 1L;
}
