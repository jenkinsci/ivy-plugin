/**
 * Copyright 2010-2011 Timothy Bingaman, Jesse Bexten
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
import hudson.model.ParametersAction;
import hudson.model.Action;
import hudson.model.Result;
import hudson.model.TaskListener;

/**
 * Invoke downstream projects with applicable parameters using Hudson's
 * DependencyGraph.Dependency interface.
 * 
 * @author tbingaman
 */
public class IvyThresholdDependency extends IvyDependency {

    private Result threshold;
    private boolean useUpstreamParameters;

    public IvyThresholdDependency(AbstractProject<?, ?> upstream, AbstractProject<?, ?> downstream, Result threshold, boolean useUpstreamParameters) {
        super(upstream, downstream);
        this.threshold = threshold;
        this.useUpstreamParameters = useUpstreamParameters;
    }

    @Override
    public boolean shouldTriggerBuild(AbstractBuild build, TaskListener listener, List<Action> actions) {
        if (build.getResult().isBetterOrEqualTo(threshold))
        {
        	if(useUpstreamParameters)
        	{
        		List<ParametersAction> paramActions = build.getActions(ParametersAction.class);
        	
        		for (ParametersAction parametersAction : paramActions) {
        			actions.add(parametersAction);
        		}
        	}
            return true;
        }
        return false;
    }

}
