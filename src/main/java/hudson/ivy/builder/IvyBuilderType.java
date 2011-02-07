/*
 * The MIT License
 * 
 * Copyright (c) 2010-2011, Timothy Bingaman
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
package hudson.ivy.builder;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Environment;
import hudson.model.Hudson;
import hudson.tasks.Builder;

public abstract class IvyBuilderType implements Describable<IvyBuilderType>, ExtensionPoint {

    @SuppressWarnings("unchecked")
    public Descriptor<IvyBuilderType> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public abstract Map<String, String> getEnvironment();

    public abstract Builder getBuilder(Properties additionalProperties, String overrideTargets, List<Environment> environment);

}
