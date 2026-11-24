package uk.dominikdias.kmpflavors.internal

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task

// Task actions must be loaded by the plugin's own classloader, not IntegrationClassLoader, or Gradle
// cannot track their implementation. Integration code attaches these instead of lambdas.

/** Deletes the task's outputs before it runs, for tasks that copy without removing stale files. */
internal class DeleteOutputsAction : Action<Task> {
    override fun execute(task: Task) {
        task.outputs.files.forEach { it.deleteRecursively() }
    }
}

/** Fails the task with [message]. */
internal class FailAction(
    private val message: String,
) : Action<Task> {
    override fun execute(task: Task): Unit = throw GradleException(message)
}
