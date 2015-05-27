package org.jenkinsci.plugins.pretestedintegration;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.pretestedintegration.exceptions.IntegationFailedExeception;
import org.jenkinsci.plugins.pretestedintegration.exceptions.NothingToDoException;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.pretestedintegration.exceptions.UnsupportedConfigurationException;

public abstract class IntegrationStrategy implements Describable<IntegrationStrategy>, ExtensionPoint {

    private final static Logger logger = Logger.getLogger(IntegrationStrategy.class.getName());

    public abstract void integrate(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener, AbstractSCMBridge bridge) throws IntegationFailedExeception, NothingToDoException, UnsupportedConfigurationException;

    @Override
    public Descriptor<IntegrationStrategy> getDescriptor() {
        logger.entering("IntegrationStrategy", "getDescriptor");// Generated code DONT TOUCH! Bookmark: 24cc4de9955cf69f2428d18f247547c0
        logger.exiting("IntegrationStrategy", "getDescriptor");// Generated code DONT TOUCH! Bookmark: ecd722247263f21a17188169745720f1´
        return (IntegrationStrategyDescriptor<?>) Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    public static DescriptorExtensionList<IntegrationStrategy, IntegrationStrategyDescriptor<IntegrationStrategy>> all() {
        logger.entering("IntegrationStrategy", "all");// Generated code DONT TOUCH! Bookmark: b760438f7e1423732caaa9ef553c5f93
        logger.exiting("IntegrationStrategy", "all");// Generated code DONT TOUCH! Bookmark: 4a32ea823412bf7eb75d28dd9edca807
        return Jenkins.getInstance().<IntegrationStrategy, IntegrationStrategyDescriptor<IntegrationStrategy>>getDescriptorList(IntegrationStrategy.class);
    }
    
    protected PersonIdent getPersonIdent(String commitAuthor) {
        //john Doe <Joh@praqma.net> 1442321765 +0200
        int endOfName = commitAuthor.indexOf("<");
        String authorName = commitAuthor.substring(0, endOfName-1);
        int endOfMail = commitAuthor.indexOf(">");
        String authorMail = commitAuthor.substring(endOfName + 1, endOfMail);
        int endOfTime = commitAuthor.indexOf(" ", endOfMail+2);
        long time = Long.parseLong(commitAuthor.substring(endOfMail + 2, endOfTime));
        int zone = Integer.parseInt(commitAuthor.substring(commitAuthor.indexOf(" ", commitAuthor.indexOf(">")+2)+1));
        return new PersonIdent(authorName, authorMail, time, zone);
    }
}
