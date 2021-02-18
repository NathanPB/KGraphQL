package com.apurebase.kgraphql.schema.dsl

import com.apurebase.kgraphql.schema.Publisher
import com.apurebase.kgraphql.schema.Schema
import com.apurebase.kgraphql.schema.SchemaException
import com.apurebase.kgraphql.schema.dsl.operations.MutationDSL
import com.apurebase.kgraphql.schema.dsl.operations.QueryDSL
import com.apurebase.kgraphql.schema.dsl.operations.SubscriptionDSL
import com.apurebase.kgraphql.schema.dsl.types.*
import com.apurebase.kgraphql.schema.model.EnumValueDef
import com.apurebase.kgraphql.schema.model.MutableSchemaDefinition
import com.apurebase.kgraphql.schema.model.TypeDef
import com.apurebase.kgraphql.schema.structure.SchemaCompilation
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

/**
 * SchemaBuilder exposes rich DSL to setup GraphQL schema
 */
class SchemaBuilder internal constructor() {

    @PublishedApi
    internal val model = MutableSchemaDefinition()

    @PublishedApi
    internal var configuration = SchemaConfigurationDSL()

    fun build(): Schema {
        return runBlocking {
            SchemaCompilation(configuration.build(), model.toSchemaDefinition()).perform()
        }
    }

    fun configure(block: SchemaConfigurationDSL.() -> Unit){
        configuration.update(block)
    }

    //================================================================================
    // OPERATIONS
    //================================================================================

    fun query(name: String, init: QueryDSL.() -> Unit): Publisher {
        val query = QueryDSL(name)
            .apply(init)
            .toKQLQuery()
        model.addQuery(query)
        return query
    }

    fun mutation(name: String, init: MutationDSL.() -> Unit): Publisher {
        val mutation = MutationDSL(name)
            .apply(init)
            .toKQLMutation()

        model.addMutation(mutation)
        return mutation
    }

    fun subscription(name : String, init: SubscriptionDSL.() -> Unit){
        val subscription = SubscriptionDSL(name)
            .apply(init)
            .toKQLSubscription()

        model.addSubscription(subscription)
    }

    //================================================================================
    // SCALAR
    //================================================================================
    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> stringScalar(kClass: KClass<T>, block: ScalarDSL<T, String>.() -> Unit) {
        val scalar = StringScalarDSL(kClass).apply(block)

        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, typeOf<T>(), scalar.createCoercion(), scalar.description))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> stringScalar(noinline block: ScalarDSL<T, String>.() -> Unit) {
        stringScalar(T::class, block)
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> intScalar(kClass: KClass<T>, block: ScalarDSL<T, Int>.() -> Unit) {
        val scalar = IntScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, typeOf<T>(), scalar.createCoercion(), scalar.description))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> intScalar(noinline block: ScalarDSL<T, Int>.() -> Unit) {
        intScalar(T::class, block)
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> floatScalar(kClass: KClass<T>, block: ScalarDSL<T, Double>.() -> Unit) {
        val scalar = DoubleScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, typeOf<T>(), scalar.createCoercion(), scalar.description))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> floatScalar(noinline block: ScalarDSL<T, Double>.() -> Unit) {
        floatScalar(T::class, block)
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> longScalar(kClass: KClass<T>, block: ScalarDSL<T, Long>.() -> Unit) {
        val scalar = LongScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, typeOf<T>(), scalar.createCoercion(), scalar.description))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> longScalar(noinline block: ScalarDSL<T, Long>.() -> Unit) {
        longScalar(T::class, block)
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> booleanScalar(kClass: KClass<T>, block: ScalarDSL<T, Boolean>.() -> Unit) {
        val scalar = BooleanScalarDSL(kClass).apply(block)
        configuration.appendMapper(scalar, kClass)
        model.addScalar(TypeDef.Scalar(scalar.name, kClass, typeOf<T>(), scalar.createCoercion(), scalar.description))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> booleanScalar(noinline block: ScalarDSL<T, Boolean>.() -> Unit) {
        booleanScalar(T::class, block)
    }

    //================================================================================
    // TYPE
    //================================================================================
    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> type(kClass: KClass<T>, block: TypeDSL<T>.() -> Unit) {
        val type = TypeDSL(model.unionsMonitor, kClass, typeOf<T>()).apply(block)
        model.addObject(type.toKQLObject())
    }

    inline fun <reified T : Any> type(noinline block: TypeDSL<T>.() -> Unit) {
        type(T::class, block)
    }

    inline fun <reified T : Any> type() {
        type(T::class, {})
    }

    //================================================================================
    // ENUM
    //================================================================================

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Enum<T>> enum(kClass: KClass<T>, enumValues: Array<T>, noinline block: (EnumDSL<T>.() -> Unit)? = null) {
        val type = EnumDSL(kClass).apply {
            if (block != null) {
                block()
            }
        }

        val kqlEnumValues = enumValues.map { value ->
            type.valueDefinitions[value]?.let { valueDSL ->
                EnumValueDef (
                    value = value,
                    description = valueDSL.description,
                    isDeprecated = valueDSL.isDeprecated,
                    deprecationReason = valueDSL.deprecationReason
                )
            } ?: EnumValueDef(value)
        }

        model.addEnum(TypeDef.Enumeration(type.name, kClass, typeOf<T>(), kqlEnumValues, type.description))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Enum<T>> enum(noinline block: (EnumDSL<T>.() -> Unit)? = null) {
        val enumValues = enumValues<T>()
        if(enumValues.isEmpty()){
            throw SchemaException("Enum of type ${T::class} must have at least one value")
        } else {
            enum(T::class, enumValues<T>(), block)
        }
    }

    //================================================================================
    // UNION
    //================================================================================

    fun unionType(name: String, block: UnionTypeDSL.() -> Unit): TypeID {
        val union = UnionTypeDSL().apply(block)
        model.addUnion(TypeDef.Union(name, union.possibleTypes, union.description))
        return TypeID(name)
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T: Any> unionType(noinline block: UnionTypeDSL.() -> Unit = {}): TypeID {
        if (!T::class.isSealed) throw SchemaException("Can't generate a union type out of a non sealed class. '${T::class.simpleName}'")

        return unionType(T::class.simpleName!!) {
            T::class.sealedSubclasses.forEach {
                possibleTypes.add(it to it.createType())
            }
            block()
        }
    }

    //================================================================================
    // INPUT
    //================================================================================

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> inputType(kClass: KClass<T>, block: InputTypeDSL<T>.() -> Unit) {
        val input = InputTypeDSL(kClass).apply(block)
        model.addInputObject(TypeDef.Input(input.name, kClass, typeOf<T>(), input.description))
    }

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T : Any> inputType(noinline block : InputTypeDSL<T>.() -> Unit = {}) {
        inputType(T::class, block)
    }
}

inline fun <T: Any, reified Raw: Any> SchemaConfigurationDSL.appendMapper(scalar: ScalarDSL<T, Raw>, kClass: KClass<T>) {
    objectMapper.registerModule(SimpleModule().addDeserializer(kClass.java, object : UsesDeserializer<T>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): T? {
            return scalar.deserialize?.invoke(p.readValueAs(Raw::class.java))
        }
    }))
}

open class UsesDeserializer<T>(vc: Class<*>? = null) : StdDeserializer<T>(vc) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): T? = TODO("Implement")
}
