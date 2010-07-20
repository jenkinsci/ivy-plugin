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

    @Override
    public Descriptor<IvyBuilderType> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public abstract Map<String, String> getEnvironment();

    public abstract Builder getBuilder(Properties additionalProperties, String overrideTargets, List<Environment> environment);

}
