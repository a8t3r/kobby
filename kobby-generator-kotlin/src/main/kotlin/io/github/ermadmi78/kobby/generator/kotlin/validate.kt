package io.github.ermadmi78.kobby.generator.kotlin

import io.github.ermadmi78.kobby.model.KobbyDirective
import io.github.ermadmi78.kobby.model.KobbyField
import io.github.ermadmi78.kobby.model.KobbySchema

fun KobbySchema.validateKotlin(layout: KotlinLayout): List<String> {
    val warnings = mutableListOf<String>()

    fun KobbyField.checkSelection(): String? =
        if (selection && layout.entity.projection.enableNotationWithoutParentheses) {
            "Restriction violated [${node.name}.$name]: " +
                    "The @${KobbyDirective.SELECTION} directive cannot be applied because " +
                    "its processing is not allowed if parenthesis-less notation is enabled."
        } else null

    interfaces { node ->
        node.fields { field ->
            field.checkSelection()?.let { warnings.add(it) }
        }
    }

    objects { node ->
        node.fields { field ->
            field.checkSelection()?.let { warnings.add(it) }
        }
    }

    return warnings
}
