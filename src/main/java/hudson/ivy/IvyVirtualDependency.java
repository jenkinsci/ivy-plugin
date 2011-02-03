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

import java.util.List;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TaskListener;

/**
 * Represents a dependency that never triggers a downstream build.
 * 
 * Used to represent the association between an Ivy Project configured to build
 * its modules as separate jobs and downstream projects depending on its
 * modules. This is required to make downstream projects behave properly when
 * they have their "Block build when upstream project is building" option
 * enabled.
 * 
 * @author tbingaman
 */
public class IvyVirtualDependency extends IvyDependency {

    public IvyVirtualDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream) {
        super(upstream, downstream);
    }

    @Override
    public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener, List<Action> actions) {
        return false;
    }

}
