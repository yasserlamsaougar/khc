package fr.x.generator

import com.github.javafaker.Faker
import io.kotlintest.matchers.string.contain
import io.kotlintest.matchers.types.shouldBeTypeOf
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNot
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.DescribeSpec
import java.io.File

class ExtentedFaker : Faker() {
    fun test1(): String {
        return "test1"
    }

    fun test2(): String {
        return "test2"
    }
}

class GeneratorTest : DescribeSpec({
    describe("A generator") {
        val faker = ExtentedFaker()
        val generator = Generator()
        val pathToTemplate1 = "src/test/resources/template_1.txt"
        it("should read a file and returns its content") {
            generator.readFile(File(pathToTemplate1)) shouldBe (ExistentTemplate("some_text"))
        }
        it("should try to read a file and return a non existent Template") {
            generator.readFile(File("somePath")) shouldBe (NonExistentTemplate)
        }

        it("should be able to handle templates without expressions") {
            val existentTemplate = ExistentTemplate("This is a simple test")
            generator.findVariables(faker, existentTemplate).first shouldBe (existentTemplate)
            generator.findVariables(faker, existentTemplate).second shouldBe emptySet()
        }
        it("should be able to find a variable inside a template file") {
            val existentTemplate = ExistentTemplate("This is a simple test %%test1%%")
            generator.findVariables(faker, existentTemplate).first shouldBe (existentTemplate)
            val expressions = generator.findVariables(faker, existentTemplate).second
            expressions.forEach {
                it.expression.invoke() shouldNotBe null
            }
        }
        it("should be able to find all the variables inside a template file") {
            val existentTemplate =
                ExistentTemplate("This is a more complex test %%test2%% with many expressions %% test1 %%")
            generator.findVariables(faker, existentTemplate).first shouldBe (existentTemplate)
            val expressions = generator.findVariables(faker, existentTemplate).second
            expressions.forEach {
                it.expression.invoke() shouldNotBe null
            }
        }
        it("should be able to find all the variables inside a template file and evaluate them") {
            val existentTemplate =
                ExistentTemplate("This is a more complex test %%test1%% with many expressions %% test2 %%")
            generator.findVariables(faker, existentTemplate).first shouldBe (existentTemplate)
            val expressions = generator.findVariables(faker, existentTemplate).second
            expressions.forEachIndexed { index, ve ->
                ve.expression.invoke() shouldNotBe null
                ve.expression.invoke() shouldBe "test${index + 1}"
            }
        }
        it("should be able to find all the variables inside a template file and replace them") {
            val existentTemplate =
                ExistentTemplate("This is a more complex test %%name.fullName%% with many expressions %% name.firstName %%")
            val inputTemplate = Pair(
                existentTemplate, setOf(
                    VariableExpression({
                        "test1"
                    }, toReplace = "%%name.fullName%%"),
                    VariableExpression({
                        "test2"
                    }, toReplace = "%% name.firstName %%")
                )
            )
            val replacedContent = generator.replaceOccurrences(inputTemplate)
            replacedContent.shouldBeTypeOf<ExistentContent>()
            (replacedContent as ExistentContent).content shouldNot contain("%%name.fullName%%")
            replacedContent.content should contain("test1")
            (replacedContent).content shouldNot contain("%% name.firstName %%")
            replacedContent.content should contain("test2")
        }
    }


})