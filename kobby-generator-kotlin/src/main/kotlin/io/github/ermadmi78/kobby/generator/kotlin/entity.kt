package io.github.ermadmi78.kobby.generator.kotlin

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.UNIT
import io.github.ermadmi78.kobby.model.KobbyNode
import io.github.ermadmi78.kobby.model.KobbyNodeKind.OBJECT
import io.github.ermadmi78.kobby.model.KobbySchema

/**
 * Created on 24.01.2021
 *
 * @author Dmitry Ermakov (ermadmi78@gmail.com)
 */
internal fun generateEntity(schema: KobbySchema, layout: KotlinLayout): List<FileSpec> = with(layout) {
    val files = mutableListOf<FileSpec>()

    //******************************************************************************************************************
    //                                                Objects
    //******************************************************************************************************************
    schema.objects { node ->
        files += buildFile(entity.packageName, node.entityName) {
            buildEntity(node, layout)
            buildProjection(node, layout)
            buildSelection(node, layout)
        }
    }

    //******************************************************************************************************************
    //                                                Interfaces
    //******************************************************************************************************************
    schema.interfaces { node ->
        files += buildFile(entity.packageName, node.entityName) {
            buildEntity(node, layout)
            buildProjection(node, layout)
            buildQualification(node, layout)
            buildQualifiedProjection(node, layout)
            buildSelection(node, layout)
        }
    }

    //******************************************************************************************************************
    //                                                Unions
    //******************************************************************************************************************
    schema.unions { node ->
        files += buildFile(entity.packageName, node.entityName) {
            buildEntity(node, layout)
            buildProjection(node, layout)
            buildQualification(node, layout)
            buildQualifiedProjection(node, layout)
            buildSelection(node, layout)
        }
    }

    files
}

private fun FileSpecBuilder.buildEntity(node: KobbyNode, layout: KotlinLayout) = with(layout) {
    buildInterface(node.entityName) {
        node.implements {
            addSuperinterface(it.entityClass)
        }
        node.comments {
            addKdoc("%L", it)
        }

        if (adapter.extendedApi && node.isOperation) {
            addSuperinterface(context.responseClass)

            buildFunction(entity.errorsFunName) {
                addModifiers(ABSTRACT)
                addModifiers(OVERRIDE)
                returns(dto.errorsType)

                var kDoc = "GraphQL response errors access function generated for adapters with extended API."
                if (adapter.throwException) {
                    kDoc += " To enable GraphQL error propagation to the entity layer, " +
                            "set Kobby configuration property `adapter.throwException` to `false`."
                }
                addKdoc("%L", kDoc)
            }

            buildFunction(entity.extensionsFunName) {
                addModifiers(ABSTRACT)
                addModifiers(OVERRIDE)
                returns(dto.extensionsType)

                addKdoc(
                    "%L",
                    "GraphQL response extensions access function generated for adapters with extended API."
                )
            }
        }

        if (entity.contextFunEnabled) {
            // context access function
            buildFunction(entity.contextFunName) {
                addModifiers(ABSTRACT)
                if (node.implements.isNotEmpty()) {
                    addModifiers(OVERRIDE)
                }
                returns(context.contextClass)
            }
        }

        // withCurrentProjection
        if (node.kind == OBJECT) {
            buildFunction(entity.withCurrentProjectionFun) {
                addModifiers(ABSTRACT)
                receiver(node.projectionClass)
            }
        }

        node.fields { field ->
            buildProperty(field.name, field.entityType) {
                if (field.isOverride) {
                    addModifiers(OVERRIDE)
                }
                field.comments {
                    addKdoc("%L", it)
                }
            }
        }
    }
}

private fun FileSpecBuilder.buildProjection(node: KobbyNode, layout: KotlinLayout) = with(layout) {
    buildInterface(node.projectionName) {
        addAnnotation(context.dslClass)
        node.implements {
            addSuperinterface(it.projectionClass)
        }
        node.comments {
            addKdoc("%L", it)
        }
        node.fields.values.asSequence().filter { !it.isRequired }.forEach { field ->
            if (field.isProjectionPropertyEnabled) {
                buildProperty(field.projectionFieldName, ANY.nullable()) {
                    addModifiers(ABSTRACT)
                    if (field.isOverride) {
                        addModifiers(OVERRIDE)
                    }
                    field.comments {
                        addKdoc("%L", it)
                    }
                }
            } else {
                buildFunction(field.projectionFieldName) {
                    addModifiers(ABSTRACT)
                    if (field.isOverride) {
                        addModifiers(OVERRIDE)
                    }
                    field.comments {
                        addKdoc("%L", it)
                    }

                    field.arguments.values.asSequence()
                        .filter { !field.isSelection || !it.isInitialized }
                        .forEach { arg ->
                            buildParameter(arg.name, arg.entityType) {
                                if (!field.isOverride && arg.isInitialized && !field.isMultiBase) {
                                    defaultValue("null")
                                }
                                arg.comments {
                                    addKdoc("%L", it)
                                }
                                arg.defaultValue?.also { literal ->
                                    if (arg.comments.isNotEmpty()) {
                                        addKdoc("%L", " ");
                                    }
                                    addKdoc("%L", "Default: $literal")
                                }
                            }
                        }
                    field.lambda?.also {
                        buildParameter(it) {
                            if (!field.isOverride && field.type.node.hasDefaults) {
                                defaultValue("{}")
                            }
                        }
                    }
                }
            }
        }

        // minimize function
        buildFunction(entity.projection.minimizeFun) {
            if (node.implements.isNotEmpty()) {
                addModifiers(OVERRIDE)
            }
            node.fields.values.asSequence().filter { !it.isRequired && it.isDefault }.forEach { field ->
                addStatement("${field.projectionFieldName.escape()}()")
            }
        }
    }
}

private fun FileSpecBuilder.buildSelection(node: KobbyNode, layout: KotlinLayout) = with(layout) {
    node.fields.values.asSequence().filter { !it.isOverride && it.isSelection }.forEach { field ->
        buildInterface(field.selectionName) {
            addAnnotation(context.dslClass)
            field.comments {
                addKdoc("%L", it)
            }
            field.arguments.values.asSequence().filter { it.isInitialized }.forEach { arg ->
                buildProperty(arg.name, arg.entityType) {
                    mutable()
                    arg.comments {
                        addKdoc("%L", it)
                    }
                    arg.defaultValue?.also { literal ->
                        if (arg.comments.isNotEmpty()) {
                            addKdoc("%L", "  \n> ")
                        }
                        addKdoc("%L", "Default: $literal")
                    }
                }
            }
        }

        if (field.type.hasProjection) {
            buildInterface(field.queryName) {
                addAnnotation(context.dslClass)
                field.comments {
                    addKdoc("%L", it)
                }
                addSuperinterface(field.selectionClass)
                addSuperinterface(field.type.node.qualifiedProjectionClass)
            }
        }
    }
}

private fun FileSpecBuilder.buildQualification(node: KobbyNode, layout: KotlinLayout) = with(layout) {
    buildInterface(node.qualificationName) {
        addAnnotation(context.dslClass)
        node.comments {
            addKdoc("%L", it)
        }
        node.subObjects { subObject ->
            buildFunction(subObject.projectionOnName) {
                addModifiers(ABSTRACT)
                subObject.comments {
                    addKdoc("%L", it)
                }
                buildParameter(entity.projection.projectionArgument, subObject.projectionLambda) {
                    if (subObject.hasDefaults) {
                        defaultValue("{}")
                    }
                }
            }
        }
    }
}

private fun FileSpecBuilder.buildQualifiedProjection(node: KobbyNode, layout: KotlinLayout) = with(layout) {
    buildInterface(node.qualifiedProjectionName) {
        addAnnotation(context.dslClass)
        node.comments {
            addKdoc("%L", it)
        }
        addSuperinterface(node.projectionClass)
        addSuperinterface(node.qualificationClass)
    }
}