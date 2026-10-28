package com.sseo.feelcamera.testing

import java.io.File
import java.util.Locale
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/** adb am instrument의 종료 코드에 의존하지 않고 CI가 읽을 JUnit 결과를 기록한다. */
class CiResultListener : RunListener() {
    private data class Case(val description: Description, val started: Long = System.nanoTime(),
        var elapsed: Double = 0.0, var failure: String? = null, var skipped: Boolean = false)
    private val cases = linkedMapOf<Description, Case>()

    override fun testStarted(description: Description) { cases[description] = Case(description) }
    override fun testFailure(failure: Failure) {
        cases.getOrPut(failure.description) { Case(failure.description) }.failure = failure.trace
    }
    override fun testAssumptionFailure(failure: Failure) {
        cases.getOrPut(failure.description) { Case(failure.description) }.skipped = true
    }
    override fun testIgnored(description: Description) { cases[description] = Case(description, skipped = true) }
    override fun testFinished(description: Description) {
        cases[description]?.let { it.elapsed = (System.nanoTime() - it.started) / 1_000_000_000.0 }
    }
    override fun testRunFinished(result: Result) {
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<testsuite name=\"FeelCamera instrumentation\" tests=\"${cases.size}\" failures=\"${result.failureCount}\" errors=\"0\" skipped=\"${cases.values.count { it.skipped }}\">\n")
            cases.values.forEach { case ->
                val time = String.format(Locale.US, "%.3f", case.elapsed)
                append("  <testcase classname=\"${escape(case.description.className.orEmpty())}\" name=\"${escape(case.description.methodName ?: case.description.displayName)}\" time=\"$time\">")
                if (case.skipped) append("<skipped/>")
                case.failure?.let { append("<failure message=\"${escape(it.lineSequence().firstOrNull().orEmpty())}\">${escape(it)}</failure>") }
                append("</testcase>\n")
            }
            append("</testsuite>\n")
        }
        File(TestImages.artifacts, "junit.xml").writeText(xml)
    }

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
