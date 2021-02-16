package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.UserRemoteConfig;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assume.assumeTrue;

/**
 * Created by mads on 1/11/18.
 *
 * This test requires 3 arguments from maven in order to be executed. We've done this because we use our own repo
 * and do not want to share our credentials to GitHub with everyone.
 *
 * Run this with parameters:
 * <ul>
 *  <li>-DpasswordForTest=...</li>
 *  <li>-DusernameForTest=...</li>
 *  <li>-DrepoForTest=...</li>
 * </ul>
 */
public class GHI98_SupportForCredentialsIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    final String pw = System.getProperty("passwordForTest");
    final String uName = System.getProperty("usernameForTest");
    final String repo = System.getProperty("repoForTest");
    final String pipelineScript = "node {\n" +
        "    checkout([$class: 'GitSCM', branches: [[name: '*/ready/**']], extensions: [pretestedIntegration(gitIntegrationStrategy: squash(), integrationBranch: 'master', repoName: 'origin')], userRemoteConfigs: [[credentialsId: 'pipCredentials', url: '%repoForTest']]])\n" +
        "    pretestedIntegrationPublisher()\n" +
        "    echo \"Ohoy we've got success!\"\n" +
        "}";

    @Test
    public void testCredentialsSupportForAbstractProjectTypes() throws Exception {
        assumeTrue(pw != null && repo != null && uName != null);
        cloneTestRepositoryAndPrepareABranch();
        SystemCredentialsProvider scp = (SystemCredentialsProvider)jenkinsRule.getInstance().getExtensionList("com.cloudbees.plugins.credentials.SystemCredentialsProvider").get(0);
        UsernamePasswordCredentialsImpl unpwCred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "pipCredentials","description", uName, pw);
        scp.getCredentials().add(unpwCred);
        scp.save();
        List<UserRemoteConfig> urcList = new ArrayList<>();
        UserRemoteConfig urc = new UserRemoteConfig(repo,null,null,"pipCredentials");
        urcList.add(urc);
        FreeStyleProject freeStyle = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.SQUASH, urcList,"origin", false);
        jenkinsRule.buildAndAssertSuccess(freeStyle);
    }

    @Test
    public void testCredentialsSupportForWorkflowProjectTypes() throws Exception {
        assumeTrue(pw != null && repo != null && uName != null);
        cloneTestRepositoryAndPrepareABranch();
        SystemCredentialsProvider scp = (SystemCredentialsProvider)jenkinsRule.getInstance().getExtensionList("com.cloudbees.plugins.credentials.SystemCredentialsProvider").get(0);
        UsernamePasswordCredentialsImpl unpwCred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "pipCredentials","description", uName, pw);
        scp.getCredentials().add(unpwCred);
        scp.save();
        WorkflowJob wfJob = jenkinsRule.createProject(WorkflowJob.class, "PipTestPipeline");
        wfJob.setDefinition(new CpsFlowDefinition(pipelineScript.replace("%repoForTest", repo), true));
        jenkinsRule.buildAndAssertSuccess(wfJob);
    }

    private void cloneTestRepositoryAndPrepareABranch() throws Exception {
        File f = new File(TestUtilsFactory.WORKDIR,"testRepo");
        if(f.exists()) FileUtils.deleteDirectory(f);
        Git g = Git.cloneRepository().setURI(repo).setDirectory(f).call();
        Date d = new Date();
        File fileWithContent = new File(f, "testOutput.txt");
        g.checkout().setCreateBranch(true).setName("ready/"+d.getTime()).call();
        FileUtils.writeStringToFile(fileWithContent,"Test case "+"org.jenkinsci.plugins.pretestedintegration.integration.scm.git.GHI98_SupportForCredentialsIT committed on "+d.getTime()+"\n",true);
        g.add().addFilepattern(fileWithContent.getName()).call();
        g.commit().setAll(true).setMessage("GHI98_SupportForCredentialsIT committed on "+d.getTime()).call();
        g.push().setPushAll().setCredentialsProvider(new UsernamePasswordCredentialsProvider(uName, pw)).call();
    }

}
