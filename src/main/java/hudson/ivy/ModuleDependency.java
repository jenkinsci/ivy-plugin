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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;

/**
 * organisation + name + revision + branch.
 *
 * @author TimothyBingaman
 * @see ModuleName
 */
public final class ModuleDependency implements Serializable {
    public final String organisation;
    public final String name;
    public final String revision;
    public final String branch;

    public ModuleDependency(String organisation, String name, String revision, String branch) {
        this.organisation = organisation;
        this.name = name;
        this.revision = (revision == null || revision.startsWith("latest.") || revision.startsWith("working@") || revision.contains("${")) ? UNKNOWN : revision;
        this.branch = (branch == null || branch.contains("${")) ? UNKNOWN : branch;
    }

    public ModuleDependency(ModuleName name, String revision, String branch) {
        this(name.organisation, name.name, revision, branch);
    }

    public ModuleDependency(DependencyDescriptor dep) {
        this(dep.getDependencyRevisionId().getOrganisation(), dep.getDependencyRevisionId().getName(), dep.getDependencyRevisionId().getRevision(),
                dep.getDependencyRevisionId().getBranch());
    }

    public ModuleDependency(ModuleDescriptor module) {
        this(module.getModuleRevisionId().getOrganisation(), module.getModuleRevisionId().getName(), module.getModuleRevisionId().getRevision(),
                module.getModuleRevisionId().getBranch());
    }

    public ModuleName getName() {
        return new ModuleName(organisation, name);
    }

    /**
     * Returns organisation+name+branch with unknown revision.
     */
    public ModuleDependency withUnknownRevision() {
        return new ModuleDependency(organisation, name, UNKNOWN, branch);
    }

    /**
     * Returns organisation+name with unknown revision and branch.
     */
    public ModuleDependency withUnknownRevisionAndBranch() {
        return new ModuleDependency(organisation, name, UNKNOWN, UNKNOWN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ModuleDependency that = (ModuleDependency) o;

        return this.name.equals(that.name) && this.organisation.equals(that.organisation) && this.revision.equals(that.revision)
                && this.branch.equals(that.branch);
    }

    @Override
    public int hashCode() {
        int result;
        result = organisation.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + revision.hashCode();
        result = 31 * result + branch.hashCode();
        return result;
    }

    /**
     * For compatibility reason, this value may be used in the revision and
     * branch fields to indicate that they are unknown.
     */
    public static final String UNKNOWN = "*";

    private static final long serialVersionUID = 1L;
}
