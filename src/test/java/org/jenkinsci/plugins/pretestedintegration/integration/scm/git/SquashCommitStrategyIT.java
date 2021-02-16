package org.jenkinsci.plugins.pretestedintegration.integration.scm.git;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import static org.jenkinsci.plugins.pretestedintegration.integration.scm.git.TestUtilsFactory.STRATEGY_TYPE;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * <h3>Set of tests that test that we react correctly to merge conflicts</h3>
 * <p>
 * All tests here are using single repository integration, that is the default
 * Git configuration</p>
 * <p>
 * The tests are all using the 'Squash' strategy in the Pretested
 * integration plugin configuration</p>
 */
public class SquashCommitStrategyIT {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();
    private List<Repository> repositories;

    @Before
    public void setUp() throws Exception {
        repositories = new ArrayList<>();
        SquashCommitStrategyIT.class.getResource("myfile.zip");
    }

    @After
    public void tearDown() throws Exception {
        for (Repository repo : repositories) {
            TestUtilsFactory.destroyRepo(repo);
        }
    }

    @Test
    public void happyDayPipelineDeclarativeSQUASH() throws Exception {
        String script = "pipeline {\n" +
                "    agent any\n" +
                "    stages {\n" +
                "        stage(\"checkout\") {\n" +
                "            steps {\n" +
                "                checkout([$class: 'GitSCM', branches: [[name: '*/ready/**']], extensions: [pretestedIntegration(gitIntegrationStrategy: squash(), integrationBranch: 'master', repoName: 'origin')], userRemoteConfigs: [[url: '%URL']]])\n" +
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
        Repository repository = TestUtilsFactory.createValidRepository(repoName);
        repositories.add(repository);

        WorkflowJob wfj = jenkinsRule.createProject(WorkflowJob.class, "declarativePipe");
        wfj.setDefinition(new CpsFlowDefinition(script.replace("%URL","file://"+repository.getDirectory().getAbsolutePath()), true));
        jenkinsRule.buildAndAssertSuccess(wfj);
    }

    @Test
    public void oneValidFeatureBranch_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {
        String repoName = "test-repo";
        Repository repository = TestUtilsFactory.createValidRepository(repoName);
        repositories.add(repository);

        File workDir = new File(TestUtilsFactory.WORKDIR, repoName);
        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        String readmeFromDev = FileUtils.readFileToString(new File(workDir,"readme"));

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(workDir);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(workDir,"readme"));
        assertEquals(readmeFromDev, readmeFileContents);

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void oneInvalidFeatureBranch_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        Repository repository = TestUtilsFactory.createRepositoryWithMergeConflict("test-repo");
        repositories.add(repository);
        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION);
    }

    @Test
    public void twoFeatureBranchesBothValid_2BuildsAreTriggeredBothBranchesGetIntegratedBuildMarkedAsSUCCESS() throws Exception {
        Repository repository = TestUtilsFactory.createValidRepositoryWith2FeatureBranches("test-repo");
        repositories.add(repository);
        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        for (FreeStyleBuild build : builds) {
            Result result = build.getResult();
            assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
        }

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION + 2);
    }

    @Test
    public void twoFeatureBranches1ValidAnd1Invalid_2BuildsAreTriggeredValidBranchGetsIntegrated() throws Exception {
        Repository repository = TestUtilsFactory.createRepositoryWith2FeatureBranches1Valid1Invalid("test-repo");
        repositories.add(repository);

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(repository);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        Result result = builds.getFirstBuild().getResult();

        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        FreeStyleBuild lastFailedBuild = project.getLastFailedBuild();
        assertNotNull(lastFailedBuild);

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void oneValidFeatureBranchRunningOnSlave_1BuildIsTriggeredTheBranchGetsIntegratedBuildMarkedAsSUCCESS() throws Exception {
        String repoName = "test-repo";
        Repository repository = TestUtilsFactory.createValidRepository(repoName);
        repositories.add(repository);

        File workDir = new File(TestUtilsFactory.WORKDIR, repoName);
        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        String readmeFromDev = FileUtils.readFileToString(new File(TestUtilsFactory.WORKDIR,repoName + "/readme"));

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(workDir);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        String readmeFileContents = FileUtils.readFileToString(new File(workDir, "readme"));
        assertEquals(readmeFromDev, readmeFileContents);

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void oneInvalidFeatureBranchRunningOnSlave_1BuildIsTriggeredNothingGetsIntegratedBuildMarkedAsFAILURE() throws Exception {

        String repoName = "test-repo";
        Repository repository = TestUtilsFactory.createRepositoryWithMergeConflict(repoName);
        repositories.add(repository);

        File workDir = new File(TestUtilsFactory.WORKDIR, repoName);

        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDir);

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        int nextBuildNumber = project.getNextBuildNumber();
        FreeStyleBuild build = project.getBuildByNumber(nextBuildNumber - 1);

        Result result = build.getResult();
        assertTrue(result.isWorseOrEqualTo(Result.FAILURE));

        git.pull().call();

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        git.close();

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION);
    }

    @Test
    public void twoFeatureBranchesBothValidRunningOnSlave_2BuildsAreTriggeredBothBranchesGetIntegratedBuildMarkedAsSUCCESS() throws Exception {
        String repoBaseName = "twoBranches2Builds";
        File workDir = new File(TestUtilsFactory.WORKDIR, "twoBranches2Builds");

        Repository repository = TestUtilsFactory.createValidRepositoryWith2FeatureBranches(repoBaseName);
        repositories.add(repository);

        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDir);

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        assertEquals(2, project.getNextBuildNumber() - 1);

        for (FreeStyleBuild build : builds) {
            Result result = build.getResult();
            assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
        }

        git.pull().call();

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        git.close();

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION + 2);
    }

    @Test
    public void twoFeatureBranches1ValidAnd1InvalidRunningOnSlave_2BuildsAreTriggeredValidBranchGetsIntegratedBuildMarkedAsFAILURE() throws Exception {
        Repository repository = TestUtilsFactory.createRepositoryWith2FeatureBranches1Valid1Invalid("test-repo");
        repositories.add(repository);

        File workDir = new File(TestUtilsFactory.WORKDIR, "test-repo");

        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();

        Git git = Git.open(workDir);

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);

        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);

        jenkinsRule.waitUntilNoActivityUpTo(60000);

        RunList<FreeStyleBuild> builds = project.getBuilds();

        for (FreeStyleBuild fsbuild : builds) {
            String console = jenkinsRule.createWebClient().getPage(fsbuild, "console").asText();
            System.out.println(console);
        }

        Result result = builds.getFirstBuild().getResult();

        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));

        FreeStyleBuild lastFailedBuild = project.getLastFailedBuild();
        assertNotNull(lastFailedBuild);

        git.pull().call();

        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);

        git.close();

        TestCase.assertEquals("Commit count missmatch.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION + 1);
    }

    @Test
    public void singleCommit_keepsCommitMessage() throws Exception {
        // Create the test repository
        String repoName = "squash_singleCommit_keepsCommitMessage";
        Repository repository = TestUtilsFactory.createRepository(repoName, new ArrayList<TestCommit>() {
            {
                add(new TestCommit("master", "README.md", "# Test repository", "Commit 1: readme"));
                add(new TestCommit("ready/feature_1", "script.groovy", "println 'Hello, world!'", "Commit 2: Groovy Script"));
                add(new TestCommit("master", "README.md", "Just a test repository containing a Groovy 'Hello, world!'", "Commit 3: readme"));
            }
        });
        repositories.add(repository);

        // Clone test repo
        File workDir = new File(TestUtilsFactory.WORKDIR, repoName);
        Git.cloneRepository().setURI("file:///" + repository.getDirectory().getAbsolutePath()).setDirectory(workDir)
                .setBare(false)
                .setCloneAllBranches(true)
                .setNoCheckout(false)
                .call().close();
        Git git = Git.open(workDir);

        final int COMMIT_COUNT_BEFORE_EXECUTION = TestUtilsFactory.countCommits(git);
        // Build the project, assert SUCCESS
        FreeStyleProject project = TestUtilsFactory.configurePretestedIntegrationPlugin(jenkinsRule, STRATEGY_TYPE.SQUASH, repository);
        TestUtilsFactory.triggerProject(project);
        jenkinsRule.waitUntilNoActivityUpTo(60000);
        FreeStyleBuild build = project.getLastBuild();
        String consoleOutput = jenkinsRule.createWebClient().getPage(build, "console").asText();
        System.out.println(consoleOutput);
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build);
        git.pull().call();
        final int COMMIT_COUNT_AFTER_EXECUTION = TestUtilsFactory.countCommits(repository);
        RevCommit lastCommit = git.log().setMaxCount(1).call().iterator().next();
        String commitMessage = lastCommit.getFullMessage();

        git.close();

        assertEquals("Single commit for rebase.", COMMIT_COUNT_AFTER_EXECUTION, COMMIT_COUNT_BEFORE_EXECUTION + 1);
        assertEquals("Commit message was altered.", "Commit 2: Groovy Script", commitMessage);
    }
}
