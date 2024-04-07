package io.github.ermadmi78.kobby.model

import io.github.ermadmi78.kobby.model.KobbyNodeKind.*
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Created on 18.01.2021
 *
 * @author Dmitry Ermakov (ermadmi78@gmail.com)
 */
class KobbyNode internal constructor(
    val schema: KobbySchema,

    val name: String,
    val kind: KobbyNodeKind,
    internal val _implements: List<String>,
    val comments: List<String>,
    val enumValues: Map<String, KobbyEnumValue>,
    val fields: Map<String, KobbyField>
) {
    val implements: Map<String, KobbyNode> by lazy {
        _implements.asSequence()
            .map { schema.interfaces[it] ?: schema.unions[it] ?: invalidSchema("Unknown type \"$it\"") }
            .map { it.name to it }
            .toMap()
    }

    val subObjects: Map<String, KobbyNode> by lazy {
        when (kind) {
            INTERFACE, UNION -> {
                val res = mutableMapOf<String, KobbyNode>()
                for (subName in (schema.subObjectsIndex[name] ?: emptySet())) {
                    schema.all[subName]?.also { subNode ->
                        if (subNode.kind == OBJECT) {
                            res[subNode.name] = subNode
                        } else {
                            res += subNode.subObjects
                        }
                    }
                }

                res
            }

            else -> emptyMap()
        }
    }

    /**
     * returns node tree hierarchy without this node
     */
    val subTree: List<KobbyNode> by lazy {
        val subNodeNames: Set<String> = schema.subObjectsIndex[name]?.takeIf { it.isNotEmpty() }
            ?: return@lazy emptyList<KobbyNode>()

        val res = mutableListOf<KobbyNode>()
        for (subName in subNodeNames) {
            schema.all[subName]?.also { subNode ->
                res += subNode
                res += subNode.subTree
            }
        }

        return@lazy res
    }

    fun implements(action: (KobbyNode) -> Unit) = implements.values.forEach(action)
    fun subObjects(action: (KobbyNode) -> Unit) = subObjects.values.forEach(action)
    fun comments(action: (String) -> Unit) = comments.forEach(action)
    fun enumValues(action: (KobbyEnumValue) -> Unit) = enumValues.values.forEach(action)
    fun fields(action: (KobbyField) -> Unit) = fields.values.forEach(action)

    val primaryKeysCount: Int by lazy {
        fields.values.count { it.isPrimaryKey }
    }

    fun primaryKeys(action: (KobbyField) -> Unit) = fields.values.asSequence()
        .filter { it.isPrimaryKey }
        .forEach(action)

    fun firstPrimaryKey(): KobbyField = fields.values.first { it.isPrimaryKey }

    val isOperation: Boolean by lazy(NONE) {
        kind == OBJECT && schema.operations.containsValue(name)
    }

    val isQuery: Boolean = schema.operations[Operation.QUERY] == name
    val isMutation: Boolean = schema.operations[Operation.MUTATION] == name
    val isSubscription: Boolean = schema.operations[Operation.SUBSCRIPTION] == name

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as KobbyNode
        return schema == other.schema && name == other.name
    }

    override fun hashCode(): Int {
        var result = schema.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override fun toString(): String {
        return "${kind.name.lowercase()} $name"
    }
}

enum class KobbyNodeKind {
    SCALAR,
    OBJECT,
    INTERFACE,
    UNION,
    ENUM,
    INPUT
}

@KobbyScope
class KobbyNodeScope internal constructor(
    val schema: KobbySchema,
    name: String,
    kind: KobbyNodeKind
) {
    private val _implements = mutableListOf<String>()
    private val comments = mutableListOf<String>()
    private val enumValues = mutableMapOf<String, KobbyEnumValue>()
    private val fields = mutableMapOf<String, KobbyField>()
    private val node = KobbyNode(schema, name, kind, _implements, comments, enumValues, fields)
    private var fieldNumber = 0

    fun addImplements(interfaceName: String) {
        _implements += interfaceName
    }

    fun addComment(comment: String) {
        comments += comment
    }

    fun addEnumValue(
        name: String,
        block: KobbyEnumValueScope.() -> Unit
    ) = KobbyEnumValueScope(schema, node, name).apply(block).build().also {
        enumValues[it.name] = it
    }

    fun addField(
        name: String,
        type: KobbyType,
        defaultValue: KobbyLiteral?,
        primaryKey: Boolean,
        required: Boolean,
        default: Boolean,
        selection: Boolean,
        block: KobbyFieldScope.() -> Unit
    ) = KobbyFieldScope(
        schema,
        node,
        name,
        type,
        defaultValue,
        fieldNumber++,
        primaryKey,
        required,
        default,
        selection
    ).apply(block).build().also {
        fields[it.name] = it
    }

    fun build(): KobbyNode = node
}