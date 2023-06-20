package dev.nesk.akkurate.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dev.nesk.akkurate.annotations.Validate
import dev.nesk.akkurate.validatables.Validatable
import java.io.OutputStreamWriter
import kotlin.reflect.KProperty1

// TODO: always implement equals, hashcode, toString
class ValidateAnnotationProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) : SymbolProcessor {
    /**
     * All the generated accessors for each property of the validatables.
     *
     * An accessor is an extension property for a [Validatable] receiver, returning the property value wrapped in another [Validatable].
     *
     * @see KSPropertyDeclaration.toValidatablePropertySpec
     */
    private val accessors: MutableSet<PropertySpec> = mutableSetOf()

    /**
     * The name of the declaration with an uppercase first character.
     */
    private val KSDeclaration.capitalizedName: String get() = simpleName.asString().replaceFirstChar { it.uppercase() }

    /**
     * The package name of the generic type wrapped by the [Validatable].
     */
    private val PropertySpec.originalPackageName: String get() = ((receiverType as ParameterizedTypeName).typeArguments.first() as ClassName).packageName

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val validatables = resolver.getSymbolsWithAnnotation(Validate::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.typeParameters.isEmpty() } // Support for generic validatables might come later.
        logger.info("Found ${validatables.count()} classes annotated with @Validate.")

        // Create two accessors for each property of a validatable, the second one enables an easy traversal within a nullable structure.
        for (validatable in validatables) {
            logger.logging("Processing class '${validatable.qualifiedName!!.asString()}' with properties:")
            for (property in validatable.getAllProperties()) {
                logger.logging("  ${property.simpleName.asString()}")
                accessors += property.toValidatablePropertySpec()
                accessors += property.toValidatablePropertySpec(withNullableReceiver = true)
            }
        }

        // Group the accessors by their package and generate a file for each package with the corresponding accessors.
        accessors
            .groupBy { it.originalPackageName }
            .forEach { (packageName, accessors) ->
                val fileName = "ValidationAccessors"
                val newPackageName = "$packageName.validation.accessors"
                logger.info("Writing accessors with namespace '$newPackageName' to file '$fileName.kt'.")

                val fileBuilder = FileSpec.builder(newPackageName, fileName)
                accessors.forEach(fileBuilder::addProperty)

                // TODO: change the ALL_FILES
                codeGenerator.createNewFile(Dependencies.ALL_FILES, newPackageName, fileBuilder.name, "kt").use { output ->
                    OutputStreamWriter(output).use { fileBuilder.build().writeTo(it) }
                }
            }

        logger.info("A total of ${validatables.count()} classes and ${accessors.size} properties were processed.")
        accessors.clear()
        return emptyList()
    }

    /**
     * Generates an extension property for the property of a validatable.
     *
     * Take this data class as an example:
     * ```
     * data class User(val name: String)
     * ```
     * Running this method on it will generate the following extension property:
     * ```
     * public val Validatable<User>.name: Validatable<String>
     *   @JvmName(name = "validatableUserName")
     *   get() = createValidatable(`value`.name)
     * ```
     *
     * If we force a nullable receiver with `withNullableReceiver = true`, it will generate:
     * ```
     * public val Validatable<User?>.name: Validatable<String?>
     *   @JvmName(name = "validatableNullableUserName")
     *   get() = createValidatable(`value`?.name)
     * ```
     * The `@JvmName` annotation is here to distinguish the signature of the two getters, since the JVM
     * ignores the nullability.
     *
     * If the property is already nullable like `val name: String?`, generating the extension property
     * with `withNullableReceiver = false` will keep the nullability on the return type:
     * ```
     * public val Validatable<User>.name: Validatable<String?>
     *   @JvmName(name = "validatableUserName")
     *   get() = createValidatable(`value`.name)
     * ```
     */
    private fun KSPropertyDeclaration.toValidatablePropertySpec(withNullableReceiver: Boolean = false): PropertySpec {
        val receiver = parentDeclaration!!
        val nullabilityText = if (withNullableReceiver) "Nullable" else ""
        val validatableOfFunction = MemberName("dev.nesk.akkurate.validatables", "validatableOf")
        val kProperty1Class = KProperty1::class.asClassName()

        return PropertySpec.builder(simpleName.asString(), type.toTypeName().toValidatableType(forceNullable = withNullableReceiver))
            .receiver(receiver.toTypeName().toValidatableType(forceNullable = withNullableReceiver))
            .getter(
                FunSpec.getterBuilder().apply {
                    addAnnotation(
                        AnnotationSpec.builder(JvmName::class)
                            .addMember("name = %S", "validatable$nullabilityText${receiver.capitalizedName}${capitalizedName}")
                            .build()
                    )

                    if (withNullableReceiver) {
                        // FIXME: The cast is a workaround for https://youtrack.jetbrains.com/issue/KT-59493, it can be removed with KT v1.9.20
                        addStatement("return %M(%T::%N as %T)", validatableOfFunction, receiver.toTypeName(), toMemberName(), kProperty1Class)
                    } else {
                        addStatement("return %M(%T::%N)", validatableOfFunction, receiver.toTypeName(), toMemberName())
                    }
                }.build()
            )
            .build()
    }

    /**
     * Wraps a type inside a [Validatable] one.
     *
     * @param forceNullable Force the wrapped type to be nullable. For example, wrapping the `String` type will produce `Validatable<String?>`.
     */
    private fun TypeName.toValidatableType(forceNullable: Boolean): ParameterizedTypeName {
        val parameterType = if (forceNullable) this.copy(nullable = true) else this
        return Validatable::class.asClassName().parameterizedBy(parameterType)
    }
}
