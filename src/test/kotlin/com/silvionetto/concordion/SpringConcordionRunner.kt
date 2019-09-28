package com.silvionetto.concordion

import org.concordion.integration.junit4.ConcordionRunner
import org.junit.Ignore
import org.junit.internal.runners.model.ReflectiveCallable
import org.junit.internal.runners.statements.Fail
import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError
import org.junit.runners.model.Statement
import org.slf4j.LoggerFactory
import org.springframework.test.annotation.ProfileValueUtils
import org.springframework.test.context.TestContextManager
import org.springframework.test.context.junit4.rules.SpringClassRule
import org.springframework.test.context.junit4.rules.SpringMethodRule
import org.springframework.test.context.junit4.statements.*
import org.springframework.util.Assert
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Method


class SpringConcordionRunner : ConcordionRunner {
    private val logger = LoggerFactory.getLogger(SpringConcordionRunner::class.java)

    private var testContextManager: TestContextManager
    private var withRulesMethod: Method

    init {
        val method = ReflectionUtils.findMethod(SpringConcordionRunner::class.java, "withRules",
                FrameworkMethod::class.java, Any::class.java, Statement::class.java)
        Assert.state(method != null, "SpringifiedConcordionRunner requires JUnit 4.12 or higher")
        ReflectionUtils.makeAccessible(method!!)
        withRulesMethod = method
    }

    private fun ensureSpringRulesAreNotPresent(testClass: Class<*>) {
        for (field in testClass.fields) {
            Assert.state(!SpringClassRule::class.java.isAssignableFrom(field.type)) {
                String.format(
                        "Detected SpringClassRule field in test class [%s], " + "but SpringClassRule cannot be used with the SpringConcordionRunner.", testClass.name)
            }
            Assert.state(!SpringMethodRule::class.java.isAssignableFrom(field.type)) {
                String.format(
                        "Detected SpringMethodRule field in test class [%s], " + "but SpringMethodRule cannot be used with the SpringConcordionRunner.", testClass.name)
            }
        }
    }

    /**
     * Creates a BlockJUnit4ClassRunner to run `klass`
     *
     * @param clazz the fixture class
     * @throws InitializationError if the test class is malformed.
     */
    @Throws(InitializationError::class)
    constructor(clazz: Class<*>): super(clazz){
        if (logger.isDebugEnabled) {
            logger.debug("SpringifiedConcordionRunner constructor called with [$clazz]")
        }
        ensureSpringRulesAreNotPresent(clazz)
        this.testContextManager = createTestContextManager(clazz)
    }

    /**
     * Create a new [TestContextManager] for the supplied test class.
     *
     * Can be overridden by subclasses.
     *
     * @param clazz the test class to be managed
     */
    private fun createTestContextManager(clazz: Class<*>): TestContextManager {
        return TestContextManager(clazz)
    }

    /**
     * Get the [TestContextManager] associated with this runner.
     */
    fun getTestContextManager(): TestContextManager {
        return this.testContextManager
    }

    /**
     * Return a description suitable for an ignored test class if the test is disabled via `@IfProfileValue` at
     * the class-level, and otherwise delegate to the parent implementation.
     *
     * @see ProfileValueUtils.isTestEnabledInThisEnvironment
     */
    override fun getDescription(): Description {
        return if (!ProfileValueUtils.isTestEnabledInThisEnvironment(testClass.javaClass)) {
            Description.createSuiteDescription(testClass.javaClass)
        } else super.getDescription()
    }

    /**
     * Check whether the test is enabled in the current execution environment.
     *
     * This prevents classes with a non-matching `@IfProfileValue`
     * annotation from running altogether, even skipping the execution of `prepareTestInstance()` methods in
     * `TestExecutionListeners`.
     *
     * @see ProfileValueUtils.isTestEnabledInThisEnvironment
     * @see org.springframework.test.annotation.IfProfileValue
     *
     * @see org.springframework.test.context.TestExecutionListener
     */
    override fun run(notifier: RunNotifier) {
        if (!ProfileValueUtils.isTestEnabledInThisEnvironment(testClass.javaClass)) {
            notifier.fireTestIgnored(description)
            return
        }
        super.run(notifier)
    }

    /**
     * Wrap the [Statement] returned by the parent implementation with a `RunBeforeTestClassCallbacks`
     * statement, thus preserving the default JUnit functionality while adding support for the Spring TestContext
     * Framework.
     *
     * @see RunBeforeTestClassCallbacks
     */
    override fun withBeforeClasses(statement: Statement): Statement {
        val junitBeforeClasses = super.withBeforeClasses(statement)
        return RunBeforeTestClassCallbacks(junitBeforeClasses, getTestContextManager())
    }

    /**
     * Wrap the [Statement] returned by the parent implementation with a `RunAfterTestClassCallbacks`
     * statement, thus preserving the default JUnit functionality while adding support for the Spring TestContext
     * Framework.
     *
     * @see RunAfterTestClassCallbacks
     */
    override fun withAfterClasses(statement: Statement): Statement {
        val junitAfterClasses = super.withAfterClasses(statement)
        return RunAfterTestClassCallbacks(junitAfterClasses, getTestContextManager())
    }

    /**
     * Delegate to the parent implementation for creating the test instance and then allow the [ ][.getTestContextManager] to prepare the test instance before returning it.
     *
     * @see TestContextManager.prepareTestInstance
     */
    @Throws(Exception::class)
    override fun createTest(): Any {
        val testInstance = super.createTest()
        getTestContextManager().prepareTestInstance(testInstance)
        return testInstance
    }

    /**
     * Perform the same logic as [BlockJUnit4ClassRunner.runChild], except that
     * tests are determined to be *ignored* by [.isTestMethodIgnored].
     */
    override fun runChild(frameworkMethod: FrameworkMethod, notifier: RunNotifier) {
        val description = describeChild(frameworkMethod)
        if (isTestMethodIgnored(frameworkMethod)) {
            notifier.fireTestIgnored(description)
        } else {
            val statement: Statement
            statement = try {
                methodBlock(frameworkMethod)
            } catch (ex: Throwable) {
                Fail(ex)
            }

            runLeaf(statement, description, notifier)
        }
    }

    /**
     * Similar to how SpringJUnit4ClassRunner has augmented BlockJUnit4ClassRunner except the callbacks relating to
     * timeout and repeat because FrameworkMethods in Concordion are not possible to be annotated.
     *
     * @see .methodInvoker
     * @see .withBeforeTestExecutionCallbacks
     * @see .withAfterTestExecutionCallbacks
     * @see .possiblyExpectingExceptions
     * @see .withBefores
     * @see .withAfters
     * @see .withRulesReflectively
     */
    override fun methodBlock(frameworkMethod: FrameworkMethod): Statement {
        val testInstance: Any
        try {
            testInstance = object : ReflectiveCallable() {
                @Throws(Throwable::class)
                override fun runReflectiveCall(): Any {
                    return createTest()
                }
            }.run()
        } catch (ex: Throwable) {
            return Fail(ex)
        }

        var statement: Statement = methodInvoker(frameworkMethod, testInstance)
        statement = withBeforeTestExecutionCallbacks(frameworkMethod, testInstance, statement)
        statement = withAfterTestExecutionCallbacks(frameworkMethod, testInstance, statement)
        statement = possiblyExpectingExceptions(frameworkMethod, testInstance, statement)
        statement = withBefores(frameworkMethod, testInstance, statement)
        statement = withAfters(frameworkMethod, testInstance, statement)
        statement = withRulesReflectively(frameworkMethod, testInstance, statement)
        return statement
    }

    /**
     * Invoke JUnit's  `withRules()` method using reflection.
     */
    private fun withRulesReflectively(frameworkMethod: FrameworkMethod, testInstance: Any, statement: Statement): Statement {
        val result = ReflectionUtils.invokeMethod(withRulesMethod, this, frameworkMethod, testInstance, statement)
        Assert.state(result is Statement, "withRules mismatch")
        return result as Statement
    }

    /**
     * Return `true` if [@Ignore][Ignore] is present for the supplied [test][FrameworkMethod] or if the test method is disabled via `@IfProfileValue`.
     *
     * @see ProfileValueUtils.isTestEnabledInThisEnvironment
     */
    private fun isTestMethodIgnored(frameworkMethod: FrameworkMethod): Boolean {
        val method = frameworkMethod.method
        return method.isAnnotationPresent(Ignore::class.java) || !ProfileValueUtils.isTestEnabledInThisEnvironment(method, testClass.javaClass)
    }

    /**
     * Wrap the supplied [Statement] with a `RunBeforeTestExecutionCallbacks` statement, thus preserving the
     * default functionality while adding support for the Spring TestContext Framework.
     *
     * @see RunBeforeTestExecutionCallbacks
     */
    private fun withBeforeTestExecutionCallbacks(frameworkMethod: FrameworkMethod, testInstance: Any, statement: Statement): Statement {
        return RunBeforeTestExecutionCallbacks(statement, testInstance, frameworkMethod.method, getTestContextManager())
    }

    /**
     * Wrap the supplied [Statement] with a `RunAfterTestExecutionCallbacks` statement, thus preserving the
     * default functionality while adding support for the Spring TestContext Framework.
     *
     * @see RunAfterTestExecutionCallbacks
     */
    private fun withAfterTestExecutionCallbacks(frameworkMethod: FrameworkMethod, testInstance: Any, statement: Statement): Statement {
        return RunAfterTestExecutionCallbacks(statement, testInstance, frameworkMethod.method, getTestContextManager())
    }

    /**
     * Wrap the [Statement] returned by the parent implementation with a `RunBeforeTestMethodCallbacks`
     * statement, thus preserving the default functionality while adding support for the Spring TestContext Framework.
     *
     * @see RunBeforeTestMethodCallbacks
     */
    override fun withBefores(frameworkMethod: FrameworkMethod, testInstance: Any, statement: Statement): Statement {
        val junitBefores = super.withBefores(frameworkMethod, testInstance, statement)
        return RunBeforeTestMethodCallbacks(junitBefores, testInstance, frameworkMethod.method, getTestContextManager())
    }

    /**
     * Wrap the [Statement] returned by the parent implementation with a `RunAfterTestMethodCallbacks`
     * statement, thus preserving the default functionality while adding support for the Spring TestContext Framework.
     *
     * @see RunAfterTestMethodCallbacks
     */
    override fun withAfters(frameworkMethod: FrameworkMethod, testInstance: Any, statement: Statement): Statement {
        val junitAfters = super.withAfters(frameworkMethod, testInstance, statement)
        return RunAfterTestMethodCallbacks(junitAfters, testInstance, frameworkMethod.method, getTestContextManager())
    }
}