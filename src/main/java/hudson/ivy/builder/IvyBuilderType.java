package hudson.ivy.builder;

import java.util.Map;
import java.util.Properties;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.Builder;

public abstract class IvyBuilderType implements Describable<IvyBuilderType> {

    @Override
    public Descriptor<IvyBuilderType> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    public abstract Map<String, String> getEnvironment();

    public abstract Builder getBuilder(Properties additionalProperties);

}
