package io.github.ermadmi78.kobby.model

import graphql.language.*
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import java.io.Reader

/**
 * Created on 19.01.2021
 *
 * @author Dmitry Ermakov (ermadmi78@gmail.com)
 */
object KobbyDirective {
    const val PRIMARY_KEY: String = "primaryKey"
    const val REQUIRED: String = "required"
    const val DEFAULT: String = "default"
    const val SELECTION: String = "selection"
}

fun parseSchema(
    directiveLayout: Map<String, String>,
    vararg schemas: Reader
): KobbySchema = TypeDefinitionRegistry().also {
    for (schema in schemas) {
        it.merge(SchemaParser().parse(schema))
    }
}.toRegistryScope(directiveLayout).parseSchemaImpl()

private fun RegistryScope.parseSchemaImpl() = KobbySchema {
    schemaDefinition?.operationTypeDefinitions?.forEach { definition ->
        Operation.of(definition.name)?.also { operation ->
            definition.typeName?.name?.trim()?.takeIf { it.isNotEmpty() }?.also { nodeName ->
                addOperation(operation, nodeName)
            }
        }
    }

    scalars.values.forEach { scalar ->
        addScalar(scalar.name) {
            scalar.comments.forEach {
                addComment(it.content)
            }
        }
    }

    val unions = mutableMapOf<String, MutableSet<String>>()
    types.values.forEach { type ->
        if (type is UnionTypeDefinition) {
            type.memberTypes.forEach {
                unions.append(it.extractName(), type.name)
            }
        }
    }

    types.values.forEach { type ->
        when (type) {
            is ObjectTypeDefinition -> addObject(type.name) {
                type.description?.content?.also {
                    addComment(it)
                }
                type.comments.forEach {
                    addComment(it.content)
                }
                type.implements.forEach {
                    addImplements(it.extractName())
                }
                unions[type.name]?.forEach {
                    addImplements(it)
                }
                type.allFields().forEach { field ->
                    addField(
                        field.name,
                        field.type.resolve(schema),
                        null,
                        field.isPrimaryKey(),
                        field.isRequired(),
                        field.isDefault(),
                        field.isSelection()
                    ) {
                        field.description?.content?.also {
                            addComment(it)
                        }
                        field.comments.forEach {
                            addComment(it.content)
                        }
                        field.inputValueDefinitions.forEach { arg ->
                            addArgument(arg.name, arg.type.resolve(schema), arg.defaultValue?.resolve()) {
                                arg.description?.content?.also {
                                    addComment(it)
                                }
                                arg.comments.forEach {
                                    addComment(it.content)
                                }
                            }
                        }
                    }
                }
            }
            is InterfaceTypeDefinition -> addInterface(type.name) {
                type.description?.content?.also {
                    addComment(it)
                }
                type.comments.forEach {
                    addComment(it.content)
                }
                type.implements.forEach {
                    addImplements(it.extractName())
                }
                type.allFields().forEach { field ->
                    addField(
                        field.name,
                        field.type.resolve(schema),
                        null,
                        field.isPrimaryKey(),
                        field.isRequired(),
                        field.isDefault(),
                        field.isSelection()
                    ) {
                        field.description?.content?.also {
                            addComment(it)
                        }
                        field.comments.forEach {
                            addComment(it.content)
                        }
                        field.inputValueDefinitions.forEach { arg ->
                            addArgument(arg.name, arg.type.resolve(schema), arg.defaultValue?.resolve()) {
                                arg.description?.content?.also {
                                    addComment(it)
                                }
                                arg.comments.forEach {
                                    addComment(it.content)
                                }
                            }
                        }
                    }
                }
            }
            is UnionTypeDefinition -> addUnion(type.name) {
                type.description?.content?.also {
                    addComment(it)
                }
                type.comments.forEach {
                    addComment(it.content)
                }
            }
            is EnumTypeDefinition -> addEnum(type.name) {
                type.description?.content?.also {
                    addComment(it)
                }
                type.comments.forEach {
                    addComment(it.content)
                }
                type.allEnumValues().forEach { enumValue ->
                    addEnumValue(enumValue.name) {
                        enumValue.description?.content?.also {
                            addComment(it)
                        }
                        enumValue.comments.forEach {
                            addComment(it.content)
                        }
                    }
                }
            }
            is InputObjectTypeDefinition -> addInput(type.name) {
                type.description?.content?.also {
                    addComment(it)
                }
                type.comments.forEach {
                    addComment(it.content)
                }
                type.allInputValues().forEach { input ->
                    addField(
                        input.name,
                        input.type.resolve(schema),
                        input.defaultValue?.resolve(),
                        primaryKey = false,
                        required = false,
                        default = false,
                        selection = false
                    ) {
                        input.description?.content?.also {
                            addComment(it)
                        }
                        input.comments.forEach {
                            addComment(it.content)
                        }
                    }
                }
            }
        }
    }
}

private class RegistryScope(
    val schemaDefinition: SchemaDefinition?,
    val directiveLayout: Map<String, String>,
    val types: Map<String, TypeDefinition<*>>,
    val scalars: Map<String, TypeDefinition<*>>,
    val directives: Map<String, DirectiveDefinition>,
    val scalarExtensions: Map<String, List<ScalarTypeExtensionDefinition>>,
    val objectExtensions: Map<String, List<ObjectTypeExtensionDefinition>>,
    val interfaceExtensions: Map<String, List<InterfaceTypeExtensionDefinition>>,
    val unionExtensions: Map<String, List<UnionTypeExtensionDefinition>>,
    val enumExtensions: Map<String, List<EnumTypeExtensionDefinition>>,
    val inputObjectExtensions: Map<String, List<InputObjectTypeExtensionDefinition>>,
) {
    fun ObjectTypeDefinition.allFields(): Sequence<FieldDefinition> = sequence {
        yieldAll(fieldDefinitions)
        objectExtensions[name]?.forEach {
            yieldAll(it.fieldDefinitions)
        }
    }

    fun InterfaceTypeDefinition.allFields(): Sequence<FieldDefinition> = sequence {
        yieldAll(fieldDefinitions)
        interfaceExtensions[name]?.forEach {
            yieldAll(it.fieldDefinitions)
        }
    }

    fun EnumTypeDefinition.allEnumValues(): Sequence<EnumValueDefinition> = sequence {
        yieldAll(enumValueDefinitions)
        enumExtensions[name]?.forEach {
            yieldAll(it.enumValueDefinitions)
        }
    }

    fun InputObjectTypeDefinition.allInputValues(): Sequence<InputValueDefinition> = sequence {
        yieldAll(inputValueDefinitions)
        inputObjectExtensions[name]?.forEach {
            yieldAll(it.inputValueDefinitions)
        }
    }

    fun FieldDefinition.isPrimaryKey(): Boolean = directives.firstOrNull {
        it.name == (directiveLayout[KobbyDirective.PRIMARY_KEY] ?: KobbyDirective.PRIMARY_KEY)
    } != null

    fun FieldDefinition.isRequired(): Boolean = directives.firstOrNull {
        it.name == (directiveLayout[KobbyDirective.REQUIRED] ?: KobbyDirective.REQUIRED)
    } != null

    fun FieldDefinition.isDefault(): Boolean = directives.firstOrNull {
        it.name == (directiveLayout[KobbyDirective.DEFAULT] ?: KobbyDirective.DEFAULT)
    } != null

    fun FieldDefinition.isSelection(): Boolean = directives.firstOrNull {
        it.name == (directiveLayout[KobbyDirective.SELECTION] ?: KobbyDirective.SELECTION)
    } != null
}

private fun TypeDefinitionRegistry.toRegistryScope(directiveLayout: Map<String, String>): RegistryScope =
    RegistryScope(
        schemaDefinition().orElse(null),
        directiveLayout,
        types(),
        scalars(),
        directiveDefinitions,
        scalarTypeExtensions(),
        objectTypeExtensions(),
        interfaceTypeExtensions(),
        unionTypeExtensions(),
        enumTypeExtensions(),
        inputObjectTypeExtensions()
    )

private fun Type<*>.extractName(): String = when (this) {
    is NonNullType -> type.extractName()
    is ListType -> type.extractName()
    is TypeName -> name
    else -> error("Unexpected Type successor: ${this::javaClass.name}")
}

private fun Type<*>.resolve(schema: KobbySchema, nullable: Boolean = true): KobbyType = when (this) {
    is NonNullType -> type.resolve(schema, false)
    is ListType -> KobbyListType(type.resolve(schema), nullable)
    is TypeName -> KobbyNodeType(schema, name, nullable)
    else -> error("Unexpected Type successor: ${this::javaClass.name}")
}

private fun Value<*>.resolve(): KobbyLiteral = when (this) {
    is NullValue -> KobbyNullLiteral
    is BooleanValue -> KobbyBooleanLiteral(isValue)
    is IntValue -> KobbyIntLiteral(value)
    is FloatValue -> KobbyFloatLiteral(value)
    is StringValue -> KobbyStringLiteral(value)
    is EnumValue -> KobbyEnumLiteral(name)
    is ArrayValue -> KobbyListLiteral(values.map { it.resolve() })
    is ObjectValue -> KobbyObjectLiteral(
        objectFields.asSequence()
            .map { it.name to it.value.resolve() }
            .toMap()
    )
    is VariableReference -> KobbyVariableLiteral(name)
    else -> error("Unknown value type: ${this::class.java}")
}