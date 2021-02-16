package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import java.io.File;
import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;
import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory.STRATEGY_TYPE;

/**
 * <h3>Set of tests that test that we react correctly to merge conflicts</h3>
 * <p>
 * All tests here are using single repository integration, that is the default
 * Git configuration</p>
 * <p>
 * The tests are all using the 'Accumulated commit' strategy in the Pretested
 * integration plugin configuration</p>
 */
public class AccumulatedCommitStrategyIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private static final String FEATURE_BRANCH_NAME = "ready/feature_1";
    private Repository repository;

    @After
    public void tearDown() throws Exception {
        TestUtilsFactory.destroyRepo(repository);
    }

    /**
     * Git Plugin
     *
     * Test that show that a ready/feature_1 branch get integrated into master
     *
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     *
     * GitSCM:
     *  - 'Name' : (empty)
     *
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     *
     * Results
     *  - We expect that the plugin triggers, and that the commits on ready branch
     *    is merged into our integration branch master and build result becomes SUCCESS.
     *
     * @throws Exception
     */
    @Test
    public void oneValidFeatureBranch_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");

        File workDir = new File(TestUtilsFactory.WORKDIR,"test-repo");

        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDir);

        System.out.println("Opening git repository in: " + workDir.getAbsolutePath());

        String readmeFromIntegration = FileUtils.readFileToString(new File(workDir,"readme"));

        git.checkout().setName(FEATURE_BRANCH_NAME).setUpstreamMode(SetupUpstreamMode.TRACK).setCreateBranch(true).call();
        final int COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);
        git.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, TestUtilsFactory.STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        for (FreeStyleBuild b : builds) {
            String console = jenkinsRule.createWebClient().getPage(b, "console").asText();
            System.out.println(console);
        }

        String readmeFileContents = FileUtils.readFileToString(new File(workDir,"readme"));
        assertEquals(readmeFromIntegration, readmeFileContents);

        git.pull().call();

        final int COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION = TestUtilsFactory.countCommits(git);

        git.close();

        //We assert that 2 commits from branch gets merged + 1 combined merge commit since we do --no-ff
        assertEquals(COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION + 3, COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION);
    }

    /**
     * Git Plugin
     *
     * Test that shows that a ready/feature_1 branch does not get integrated to
     * master branch because of merge conflict.
     *
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     *
     * GitSCM:
     *  - 'Name' : (empty)
     *
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     *
     * Results
     *  - We expect that the plugin triggers, and that the build is FAILED
     *    with a merge conflict error.
     *
     * @throws Exception
     */
    @Test
    public void oneInvalidFeatureBranch_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        jenkinsRule.assertBuildStatus(Result.FAILURE, build);
    }

    /**
     * Test that show that a ready/feature_1 branch get integrated into master
     *
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     *
     * GitSCM:  
     *  - 'Name' : (empty)
     *
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     *
     * Results
     *  - We expect that the plugin triggers, and that the commits on ready branch
     *    is merged into our integration branch master and build result becomes SUCCESS.
     *
     * @throws Exception
     */
    @Test
    public void oneValidFeatureBranchRunningOnSlave_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {
        repository = TestUtilsFactory.createValidRepository("test-repo");

        File workDir = new File(TestUtilsFactory.WORKDIR,"test-repo");

        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDir);

        String fromIntegration = FileUtils.readFileToString(new File(workDir, "readme"));

        git.checkout().setName(FEATURE_BRANCH_NAME).setUpstreamMode(SetupUpstreamMode.TRACK).setCreateBranch(true).call();
        final int COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);
        git.checkout().setName("master").setUpstreamMode(SetupUpstreamMode.TRACK).call();

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(workDir, "readme"));
        assertEquals(fromIntegration, readmeFileContents);

        git.pull().call();

        final int COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION = TestUtilsFactory.countCommits(git);

        git.close();

        assertTrue(COMMIT_COUNT_ON_MASTER_AFTER_EXECUTION == COMMIT_COUNT_ON_FEATURE_BEFORE_EXECUTION + 3);
    }

    /**
     * Test that shows that a ready/feature_1 branch does not get integrated to
     * master branch because of merge conflict.
     *
     * Pretested integration:
     *  - 'Integration branch' : master (default)
     *  - 'Repository name' : origin (default) 
     *  - 'Strategy' : Squash Commit
     *
     * GitSCM:
     *  - 'Name' : (empty)
     *
     * Workflow
     *  - Create a repository containing a 'ready' branch.
     *  - The build is triggered. 
     *
     * Results
     *  - We expect that the plugin triggers, and that the build is FAILED
     *    with a merge conflict error.
     *
     * @throws Exception
     */
    @Test
    public void oneInvalidFeatureBranchRunningOnSlave_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.ACCUMULATED, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        FreeStyleBuild b = project.getFirstBuild();
        jenkinsRule.assertBuildStatus(Result.FAILURE, b);

    }

    @Test
    public void happyDayPipelineDeclarativeACCUMULATED() throws Exception {
        String script = "pipeline {\n" +
                "    agent any\n" +
                "    stages {\n" +
                "        stage(\"checkout\") {\n" +
                "            steps {\n" +
                "                checkout([$class: 'GitSCM', branches: [[name: '*/ready/**']], extensions: [pretestedIntegration(gitIntegrationStrategy: accumulated(), integrationBranch: 'master', repoName: 'origin')], userRemoteConfigs: [[url: '%URL']]])\n" +
                "            }\n" +
                "        }\n" +
                "        stage(\"publish\") {\n" +
                "            steps {\n" +
                "                pretestedIntegrationPublisher()    \n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";

        String repoName = "test-repo";
        repository = TestUtilsFactory.createValidRepository(repoName);

        WorkflowJob wfj = jenkinsRule.createProject(WorkflowJob.class, "declarativePipe");
        wfj.setDefinition(new CpsFlowDefinition(script.replace("%URL","file://"+repository.getDirectory().getAbsolutePath()), true));
        jenkinsRule.buildAndAssertSuccess(wfj);
    }

}
