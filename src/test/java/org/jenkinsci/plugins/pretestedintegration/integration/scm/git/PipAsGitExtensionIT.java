package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.slaves.DumbSlave;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.pretestedintegration.PretestedIntegrationPostCheckout;
import org.jenkinsci.plugins.pretestedintegration.scm.git.PretestedIntegrationAsGitPluginExt;
import org.jenkinsci.plugins.pretestedintegration.scm.git.SquashCommitStrategy;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static junit.framework.Assert.assertEquals;

public class PipAsGitExtensionIT {


    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    private Repository repo;


    public PipAsGitExtensionIT() {
    }

    @Before
    public void setUp() {


    }

    @After
    public void tearDown() throws Exception {
        TestUtilsFactory.destroyRepo(repo);

    }

    @Test
    public void gitSCMIntegrationTest() throws Exception {
        String repoName = "service-desk";

        repo = TestUtilsFactory.createValidRepository(repoName);

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();

        DumbSlave slave = jenkinsRule.createOnlineSlave();
        project.setAssignedNode(slave);


        List<GitSCMExtension> scmExtensions = new ArrayList<>();
        scmExtensions.add(new PretestedIntegrationAsGitPluginExt(new SquashCommitStrategy(), "master", "origin"));
        scmExtensions.add(new PruneStaleBranch());
        scmExtensions.add(new CleanCheckout());

        List<UserRemoteConfig> userConfigs = new ArrayList<>();
        userConfigs.add(new UserRemoteConfig("file://" + repo.getDirectory().getAbsolutePath(), "origin", null, null));


        project.getPublishersList().add(new PretestedIntegrationPostCheckout());

        GitSCM git = new GitSCM(userConfigs,
                Collections.singletonList(new BranchSpec("*/ready/**"))
                , false
                , null
                , null, null, scmExtensions);

        project.setScm(git);
        project.save();

        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        String console = jenkinsRule.createWebClient().getPage(project.getFirstBuild(), "console").asText();
        System.out.println(console);

        int commits = TestUtilsFactory.countCommits(repo);
        System.out.println(commits);
        assertEquals(3, commits);
        assertEquals(Result.SUCCESS, project.getFirstBuild().getResult());

    }


    @Test
    public void pipelineScriptTest() throws Exception {

        repo = TestUtilsFactory.createValidRepository("script-desk");

        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "foo-project");
        CpsFlowDefinition flowDef = new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "node {",
                "checkout([$class: 'GitSCM'," +
                        " branches: [[name: '*/ready/**']]," +
                        " extensions: [pretestedIntegration(gitIntegrationStrategy: squash(), integrationBranch: 'master', repoName: 'origin')]," +
                        " userRemoteConfigs: [[url: '" + "file://" + repo.getDirectory().getAbsolutePath() + "']]])",
                "pretestedIntegrationPublisher()",
                "}"), "\n"), false);

        job.setDefinition(flowDef);
        job.save();

        WorkflowRun workflow = job.scheduleBuild2(0).get();


        jenkinsRule.waitUntilNoActivityUpTo(60000);

        String console = jenkinsRule.createWebClient().getPage(job.getFirstBuild(), "console").asText();
        System.out.println(console);

        int commits = TestUtilsFactory.countCommits(repo);

        assertEquals(3, commits);
        assertEquals(Result.SUCCESS, workflow.getResult());
    }


}
