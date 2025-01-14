/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.nesk.akkurate.validatables

import dev.nesk.akkurate.Path
import dev.nesk.akkurate.constraints.Constraint
import dev.nesk.akkurate.constraints.ConstraintDescriptor
import dev.nesk.akkurate.constraints.ConstraintViolation
import kotlin.reflect.KFunction1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

public class Validatable<out T> internal constructor(private val wrappedValue: T, pathSegment: String? = null, internal val parent: Validatable<*>? = null) {
    private val path: Path = buildList {
        addAll(parent?.path ?: emptyList())
        if (!pathSegment.isNullOrEmpty()) {
            add(pathSegment)
        }
    }

    public fun path(): Path = path

    public fun unwrap(): T = wrappedValue

    public operator fun component1(): T = wrappedValue

    internal val constraints: LinkedHashSet<ConstraintDescriptor> by BubblingConstraints(parent)

    public fun registerConstraint(constraint: Constraint) {
        if (!constraint.satisfied) constraints += constraint
    }

    public fun registerConstraint(constraint: ConstraintViolation) {
        constraints += constraint
    }

    // TODO: Convert to extension function (breaking change) once JetBrains fixes imports: https://youtrack.jetbrains.com/issue/KTIJ-22147
    public inline operator fun invoke(block: Validatable<T>.() -> Unit): Unit = this.block()

    /**
     * Indicates whether some other object is "equal to" this validatable.
     *
     * Validatables are only compared against the value returned by [unwrap].
     * This allows easy comparisons between two validatables:
     *
     * ```
     * Validator<UserRegistration> {
     *     constrain { password == passwordConfirmation }
     * }
     * ```
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Validatable<*>

        return wrappedValue == other.wrappedValue
    }

    /**
     * Returns a hash code value for the object.
     *
     * The hashcode is only produced from the value returned by [unwrap],
     * according to [equals] implementation.
     */
    override fun hashCode(): Int = wrappedValue?.hashCode() ?: 0

    override fun toString(): String = "Validatable(unwrap=$wrappedValue, path=$path)"

    /**
     * Allows a [Validatable] to use the constraints collection of its parent or,
     * if it has no parent, instantiates a new dedicated collection to store them.
     */
    private class BubblingConstraints(val parent: Validatable<*>? = null) {
        val constraints by lazy { linkedSetOf<ConstraintDescriptor>() }
        operator fun getValue(thisRef: Validatable<*>, property: KProperty<*>) = parent?.constraints ?: constraints
    }
}


public fun <T : Any, V> Validatable<T>.validatableOf(getter: KProperty1<T, V>): Validatable<V> {
    return Validatable(getter.get(unwrap()), getter.name, this)
}

@JvmName("nullableValidatableOfProperty")
public fun <T : Any?, V> Validatable<T>.validatableOf(getter: KProperty1<T & Any, V>): Validatable<V?> {
    return Validatable(unwrap()?.let { getter.get(it) }, getter.name, this)
}

public fun <T : Any, V> Validatable<T>.validatableOf(getter: KFunction1<T, V>): Validatable<V> {
    return Validatable(getter.invoke(unwrap()), getter.name, this)
}

@JvmName("nullableValidatableOfFunction")
public fun <T : Any?, V> Validatable<T>.validatableOf(getter: KFunction1<T & Any, V>): Validatable<V?> {
    return Validatable(unwrap()?.let { getter.invoke(it) }, getter.name, this)
}
