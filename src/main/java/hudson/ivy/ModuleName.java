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
 * Version independent name of an Ivy module.
 * 
 * @author Timothy Bingaman
 * @see ModuleDependency
 */
public class ModuleName implements Comparable<ModuleName>, Serializable {
    public final String organisation;
    public final String name;

    public ModuleName(String organisation, String name) {
        this.organisation = organisation;
        this.name = name;
    }

    public ModuleName(ModuleDescriptor module) {
        this(module.getModuleRevisionId().getOrganisation(),module.getModuleRevisionId().getName());
    }

    public ModuleName(DependencyDescriptor dep) {
        this(dep.getDependencyId().getOrganisation(),dep.getDependencyId().getName());
    }

    /**
     * Returns the "organization:name" form.
     */
    public String toString() {
        return organisation+':'+name;
    }

    /**
     * Returns the "organisation$name" form,
     * which is safe for the use as a file name, unlike {@link #toString()}.
     */
    public String toFileSystemName() {
        return organisation+'$'+name;
    }

    public static ModuleName fromFileSystemName(String n) {
        int idx = n.indexOf('$');
        if(idx<0)   throw new IllegalArgumentException(n);
        return new ModuleName(n.substring(0,idx),n.substring(idx+1));
    }

    public static ModuleName fromString(String n) {
        int idx = Math.max(n.indexOf(':'),n.indexOf('$'));
        if(idx<0)   throw new IllegalArgumentException(n);
        return new ModuleName(n.substring(0,idx),n.substring(idx+1));
    }

    /**
     * Checks if the given name is valid module name string format
     * created by {@link #toString()}.
     */
    public static boolean isValid(String n) {
        return Math.max(n.indexOf(':'),n.indexOf('$'))>0;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModuleName that = (ModuleName) o;

        return name.equals(that.name)
            && organisation.equals(that.organisation);
    }

    public int hashCode() {
        int result;
        result = organisation.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    public int compareTo(ModuleName that) {
        int r = this.organisation.compareTo(that.organisation);
        if(r!=0)    return r;
        return this.name.compareTo(that.name);
    }

    private static final long serialVersionUID = 1L;
}
