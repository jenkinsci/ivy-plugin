# Jenkins Ivy Plugin

Provides Jenkins integration with [Apache Ivy](http://ant.apache.org/ivy/).

See [Ivy Plugin](http://wiki.jenkins-ci.org/display/JENKINS/Ivy+Plugin) on the Jenkins wiki for more
information.

This plugin automatically configures a build to trigger other builds
based on dependency configuration via [Apache
Ivy](http://ant.apache.org/ivy).

**Note**: Support for Hudson has been dropped.  Only Jenkins will be supported from now on.

## Plugin download

- [Latest hpi](http://updates.jenkins-ci.org/latest/ivy.hpi)

## Supported versions

Since Version 1.25 this plugin uses Ivy 2.4.0 internally (but it should
work with Ivy 2.+ projects).

## Creating an Ivy Project (added in version 1.0)

By using an Ivy project instead of a Freestyle project Jenkins can
simplify the job configuration. It will auto-detect all the ivy modules
you checked out, create builds for them, and automatically generate the
upstream/downstream build triggers based on your Ivy dependencies.

1.  Click on the "New Job" link
    ![](https://wiki.jenkins.io/download/attachments/26574871/new-job.png?version=1&modificationDate=1266434046000&api=v2)
2.  Enter a project name and choose "Build an Ivy project"
    ![](https://wiki.jenkins.io/download/attachments/26574871/new-ivy-project.png?version=1&modificationDate=1266434112000&api=v2)
3.  Fill in your SCM details (you can choose to check out a single ivy
    module, or an entire branch of ivy modules)
4.  Choose the Ant version to build with and enter the targets you want
    run. Most of the options have sensible defaults so you only need to
    fill them in if your modules are set up differently than the default
    values expect. If you want to build each module as a separate job,
    expand the "Advanced" section and check "Build modules as separate
    jobs". If you are polling for changes from your SCM, you can also
    optionally choose to only build changed modules.
    ![](https://wiki.jenkins.io/download/attachments/26574871/ivy-project-configuration.png?version=2&modificationDate=1280354523000&api=v2)
5.  Now on your Jenkins job page you will see a "Modules" link
    ![](https://wiki.jenkins.io/download/attachments/26574871/modules.png?version=1&modificationDate=1266434838000&api=v2)
6.  This will give you a view of all the discovered ivy modules you
    checked out (Note: you will need to build the project once before
    the modules show up)
    ![](https://wiki.jenkins.io/download/attachments/26574871/modules-view.png?version=1&modificationDate=1266434913000&api=v2)
7.  The upstream/downstream project build triggers will be automatically
    generated from the dependencies in your ivy files.
    ![](https://wiki.jenkins.io/download/attachments/26574871/upstream-downstream.png?version=1&modificationDate=1266435212000&api=v2)
8.  If you chose to build the modules as separate jobs, then you can
    click on a module in the module view to see all the individual
    builds, SCM changes, artifacts, etc.
9.  If you want to run different targets on a certain module then you
    can override the default targets in the module configuration.
    ![](https://wiki.jenkins.io/download/attachments/26574871/ivy-module-configuration.png?version=1&modificationDate=1266435271000&api=v2)

## Build Trigger Configuration (for Freestyle Projects)

![](https://wiki.jenkins.io/download/attachments/26574871/hudson_ivy_build_trigger.png?version=2&modificationDate=1268837194000&api=v2)

 Upon building, it scans the ivy.xml and fills in the 'build other
projects' setting, which results in output on the project page of which
projects are upstream (depends on) and which are downstream (is a
dependency).

![](https://wiki.jenkins.io/download/attachments/26574871/hudson_ivy_-_project_associations.png?version=1&modificationDate=1211130358000&api=v2)
