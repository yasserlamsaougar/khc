package fr.x.generator

import com.github.javafaker.Faker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.system.measureTimeMillis


sealed class TemplateContent
data class ExistentTemplate(val content: String) : TemplateContent()
object NonExistentTemplate : TemplateContent()

sealed class ReplacedContent
data class ExistentContent(val content: String) : ReplacedContent()
object NonExistentContent : ReplacedContent()

data class VariableExpression(val expression: () -> Any?, val toReplace: String)


class Generator {

    fun generateExpressions(file: File, faker: Faker = Faker()): Pair<TemplateContent, Set<VariableExpression>> {
        val template = this.readFile(file)
        return when (template) {
            is ExistentTemplate -> this.findVariables(faker, template)
            else -> Pair(template, emptySet())
        }
    }

    fun replace(templateExpression: Pair<TemplateContent, Set<VariableExpression>>): ReplacedContent {
        return when (templateExpression.first) {
            is ExistentTemplate -> this.replaceOccurrences(templateExpression)
            is NonExistentTemplate -> NonExistentContent
        }
    }

    fun readFile(file: File): TemplateContent {
        return if (file.exists()) {
            ExistentTemplate(file.readText())
        } else {
            NonExistentTemplate
        }
    }

    fun findVariables(
        faker: Faker,
        existentTemplate: ExistentTemplate
    ): Pair<ExistentTemplate, Set<VariableExpression>> {
        val regex = "%%\\s*([a-zA-Z0-9_$-^()]+)\\s*%%".toRegex()
        val expressions = regex.findAll(existentTemplate.content).map {
            VariableExpression(
                expression = evaluate(faker, it.groupValues.last()),
                toReplace = it.value
            )
        }.toSet()
        return Pair(existentTemplate, expressions)
    }


    fun replaceOccurrences(templates: Pair<TemplateContent, Set<VariableExpression>>): ReplacedContent {
        val templateContent = templates.first
        return when (templateContent) {
            is ExistentTemplate -> {
                val expressions = templates.second
                ExistentContent(expressions.fold(templateContent.content) { acc, variableExpression ->
                    acc.replace(variableExpression.toReplace, variableExpression.expression()?.toString().orEmpty())
                })
            }
            is NonExistentTemplate -> NonExistentContent
        }
    }

    private fun evaluate(faker: Faker, expression: String): (() -> Any?) {
        return expression.split(".").fold(ArrayDeque<() -> Any?>()) { acc, expr ->
            if (acc.isNotEmpty()) {
                val evaluator = acc.pollFirst()
                val evaluatorValue = evaluator?.invoke()
                val method = evaluatorValue!!::class.declaredMemberFunctions.find { it.name == expr }
                acc.addFirst {
                    method?.call(evaluatorValue)
                }
            } else {
                acc.addFirst {
                    faker::class.declaredMemberFunctions.find { e ->
                        e.name == expr
                    }?.call(faker)
                }
            }
            acc
        }.poll()
    }
}
