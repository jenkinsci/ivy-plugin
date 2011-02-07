/**
 * Copyright 2010-2011 Timothy Bingaman
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hudson.ivy;

import hudson.model.AbstractProject;
import hudson.model.DependencyGraph.Dependency;

/**
 * Base class representing an Ivy Project build dependency.
 * 
 * @author tbingaman
 */
public abstract class IvyDependency extends Dependency {

    public IvyDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream) {
        super(upstream, downstream);
    }

}
